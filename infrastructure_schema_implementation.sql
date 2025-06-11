-- Implementation Examples for Multi-Provider Infrastructure Schema
-- Shows how to handle provider-specific logic and migrations

-- =====================================================
-- PROVIDER-SPECIFIC ATTRIBUTE MAPPINGS
-- =====================================================

-- Function to map provider-specific fields to normalized attributes
CREATE OR REPLACE FUNCTION map_provider_attributes(
    p_provider_type VARCHAR,
    p_resource_type VARCHAR,
    p_provider_data JSONB
) RETURNS TABLE (
    attribute_name VARCHAR,
    attribute_value TEXT
) AS $$
BEGIN
    CASE p_provider_type
        WHEN 'proxmox' THEN
            CASE p_resource_type
                WHEN 'vm' THEN
                    RETURN QUERY
                    SELECT 'cpu_cores', (p_provider_data->>'cores')::TEXT
                    UNION ALL
                    SELECT 'cpu_sockets', (p_provider_data->>'sockets')::TEXT
                    UNION ALL
                    SELECT 'cpu_type', p_provider_data->>'cpu'
                    UNION ALL
                    SELECT 'memory_mb', (p_provider_data->>'memory')::TEXT
                    UNION ALL
                    SELECT 'numa_enabled', (p_provider_data->>'numa')::TEXT
                    UNION ALL
                    SELECT 'machine_type', p_provider_data->>'machine'
                    UNION ALL
                    SELECT 'bios_type', p_provider_data->>'bios'
                    UNION ALL
                    SELECT 'boot_order', p_provider_data->>'boot'
                    UNION ALL
                    SELECT 'start_on_boot', (p_provider_data->>'onboot')::TEXT;
            END CASE;
            
        WHEN 'hetzner' THEN
            CASE p_resource_type
                WHEN 'vm' THEN
                    RETURN QUERY
                    SELECT 'server_type', p_provider_data->>'server_type'
                    UNION ALL
                    SELECT 'datacenter', p_provider_data->>'datacenter'
                    UNION ALL
                    SELECT 'image', p_provider_data->>'image'
                    UNION ALL
                    SELECT 'placement_group', p_provider_data->>'placement_group'
                    UNION ALL
                    SELECT 'public_net_enabled', (p_provider_data->>'public_net'->>'enabled')::TEXT
                    UNION ALL
                    SELECT 'backup_window', p_provider_data->>'backup_window';
            END CASE;
            
        WHEN 'libvirt' THEN
            CASE p_resource_type
                WHEN 'vm' THEN
                    RETURN QUERY
                    SELECT 'domain_type', p_provider_data->>'type'
                    UNION ALL
                    SELECT 'arch', p_provider_data->>'arch'
                    UNION ALL
                    SELECT 'machine', p_provider_data->>'machine'
                    UNION ALL
                    SELECT 'emulator', p_provider_data->>'emulator'
                    UNION ALL
                    SELECT 'vcpu_placement', p_provider_data->>'vcpu_placement'
                    UNION ALL
                    SELECT 'cpu_mode', p_provider_data->'cpu'->>'mode';
            END CASE;
    END CASE;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- STORED PROCEDURES FOR RESOURCE MANAGEMENT
-- =====================================================

-- Procedure to create a resource with attributes
CREATE OR REPLACE PROCEDURE create_resource_with_attributes(
    p_resource_type VARCHAR,
    p_provider_endpoint_id INTEGER,
    p_name VARCHAR,
    p_external_id VARCHAR,
    p_attributes JSONB,
    OUT p_resource_id INTEGER
) AS $$
DECLARE
    v_resource_type_id INTEGER;
    v_provider_type_id INTEGER;
    v_attr_key TEXT;
    v_attr_value TEXT;
    v_attr_def_id INTEGER;
    v_data_type VARCHAR;
BEGIN
    -- Get resource type ID
    SELECT id INTO v_resource_type_id
    FROM resource_types
    WHERE name = p_resource_type;
    
    IF v_resource_type_id IS NULL THEN
        RAISE EXCEPTION 'Unknown resource type: %', p_resource_type;
    END IF;
    
    -- Get provider type for validation
    SELECT p.provider_type_id INTO v_provider_type_id
    FROM provider_endpoints pe
    JOIN providers p ON pe.provider_id = p.id
    WHERE pe.id = p_provider_endpoint_id;
    
    -- Insert base resource
    INSERT INTO resources (
        resource_type_id,
        provider_endpoint_id,
        name,
        external_id,
        status
    ) VALUES (
        v_resource_type_id,
        p_provider_endpoint_id,
        p_name,
        p_external_id,
        'creating'
    ) RETURNING id INTO p_resource_id;
    
    -- Insert attributes
    FOR v_attr_key, v_attr_value IN 
        SELECT key, value::text 
        FROM jsonb_each(p_attributes)
    LOOP
        -- Get attribute definition
        SELECT ad.id, ad.data_type INTO v_attr_def_id, v_data_type
        FROM attribute_definitions ad
        WHERE ad.name = v_attr_key;
        
        IF v_attr_def_id IS NULL THEN
            CONTINUE; -- Skip unknown attributes
        END IF;
        
        -- Validate attribute is valid for this resource/provider combination
        IF NOT EXISTS (
            SELECT 1 FROM resource_type_attributes rta
            WHERE rta.resource_type_id = v_resource_type_id
            AND (rta.provider_type_id IS NULL OR rta.provider_type_id = v_provider_type_id)
            AND rta.attribute_definition_id = v_attr_def_id
        ) THEN
            CONTINUE; -- Skip invalid attributes for this combination
        END IF;
        
        -- Insert attribute with proper type
        INSERT INTO resource_attributes (
            resource_id,
            attribute_definition_id,
            value_string,
            value_integer,
            value_decimal,
            value_boolean,
            value_datetime
        ) VALUES (
            p_resource_id,
            v_attr_def_id,
            CASE WHEN v_data_type = 'string' THEN v_attr_value ELSE NULL END,
            CASE WHEN v_data_type = 'integer' THEN v_attr_value::BIGINT ELSE NULL END,
            CASE WHEN v_data_type = 'decimal' THEN v_attr_value::DECIMAL ELSE NULL END,
            CASE WHEN v_data_type = 'boolean' THEN v_attr_value::BOOLEAN ELSE NULL END,
            CASE WHEN v_data_type = 'datetime' THEN v_attr_value::TIMESTAMP ELSE NULL END
        );
    END LOOP;
    
    -- Add state history entry
    INSERT INTO resource_state_history (
        resource_id,
        previous_state,
        new_state,
        reason,
        initiated_by
    ) VALUES (
        p_resource_id,
        NULL,
        'creating',
        'Resource created',
        'system'
    );
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- MIGRATION HELPERS
-- =====================================================

-- Function to import existing Proxmox VMs
CREATE OR REPLACE FUNCTION import_proxmox_vm(
    p_provider_endpoint_id INTEGER,
    p_proxmox_data JSONB
) RETURNS INTEGER AS $$
DECLARE
    v_resource_id INTEGER;
    v_vm_data JSONB;
BEGIN
    -- Parse Proxmox VM data
    v_vm_data := jsonb_build_object(
        'cpu_cores', p_proxmox_data->>'cores',
        'cpu_sockets', p_proxmox_data->>'sockets',
        'cpu_type', p_proxmox_data->>'cpu',
        'memory_mb', p_proxmox_data->>'memory',
        'numa_enabled', p_proxmox_data->>'numa',
        'machine_type', p_proxmox_data->>'machine',
        'bios_type', p_proxmox_data->>'bios',
        'start_on_boot', p_proxmox_data->>'onboot',
        'protection', p_proxmox_data->>'protection'
    );
    
    -- Create the resource
    CALL create_resource_with_attributes(
        'vm',
        p_provider_endpoint_id,
        p_proxmox_data->>'name',
        p_proxmox_data->>'vmid',
        v_vm_data,
        v_resource_id
    );
    
    -- Insert into VM-specific table
    INSERT INTO virtual_machines (
        resource_id,
        cpu_cores,
        memory_mb,
        primary_disk_gb,
        operating_system,
        hypervisor_type,
        is_template
    ) VALUES (
        v_resource_id,
        (p_proxmox_data->>'cores')::INTEGER,
        (p_proxmox_data->>'memory')::INTEGER,
        (p_proxmox_data->>'disk')::INTEGER / 1024, -- Convert to GB
        p_proxmox_data->>'ostype',
        'qemu',
        (p_proxmox_data->>'template')::BOOLEAN
    );
    
    -- Update status based on Proxmox status
    UPDATE resources 
    SET status = CASE 
        WHEN p_proxmox_data->>'status' = 'running' THEN 'running'
        WHEN p_proxmox_data->>'status' = 'stopped' THEN 'stopped'
        ELSE 'unknown'
    END
    WHERE id = v_resource_id;
    
    RETURN v_resource_id;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- CROSS-PROVIDER OPERATIONS
-- =====================================================

-- Function to find equivalent resources across providers
CREATE OR REPLACE FUNCTION find_equivalent_resources(
    p_resource_id INTEGER,
    p_similarity_threshold DECIMAL DEFAULT 0.8
) RETURNS TABLE (
    resource_id INTEGER,
    provider_type VARCHAR,
    provider_name VARCHAR,
    resource_name VARCHAR,
    similarity_score DECIMAL
) AS $$
DECLARE
    v_cpu_cores INTEGER;
    v_memory_mb INTEGER;
    v_disk_gb INTEGER;
    v_resource_type_id INTEGER;
BEGIN
    -- Get source resource specs
    SELECT 
        vm.cpu_cores,
        vm.memory_mb,
        vm.primary_disk_gb,
        r.resource_type_id
    INTO v_cpu_cores, v_memory_mb, v_disk_gb, v_resource_type_id
    FROM resources r
    JOIN virtual_machines vm ON r.id = vm.resource_id
    WHERE r.id = p_resource_id;
    
    IF v_cpu_cores IS NULL THEN
        RAISE EXCEPTION 'Resource % is not a virtual machine', p_resource_id;
    END IF;
    
    -- Find similar resources
    RETURN QUERY
    SELECT 
        r.id,
        pt.name,
        p.name,
        r.name,
        (
            -- Calculate similarity score based on specs
            (1 - ABS(vm.cpu_cores - v_cpu_cores)::DECIMAL / GREATEST(vm.cpu_cores, v_cpu_cores)) * 0.4 +
            (1 - ABS(vm.memory_mb - v_memory_mb)::DECIMAL / GREATEST(vm.memory_mb, v_memory_mb)) * 0.4 +
            (1 - ABS(vm.primary_disk_gb - v_disk_gb)::DECIMAL / GREATEST(vm.primary_disk_gb, v_disk_gb)) * 0.2
        ) as similarity
    FROM resources r
    JOIN virtual_machines vm ON r.id = vm.resource_id
    JOIN provider_endpoints pe ON r.provider_endpoint_id = pe.id
    JOIN providers p ON pe.provider_id = p.id
    JOIN provider_types pt ON p.provider_type_id = pt.id
    WHERE r.id != p_resource_id
        AND r.resource_type_id = v_resource_type_id
        AND r.deleted_at IS NULL
    HAVING (
        (1 - ABS(vm.cpu_cores - v_cpu_cores)::DECIMAL / GREATEST(vm.cpu_cores, v_cpu_cores)) * 0.4 +
        (1 - ABS(vm.memory_mb - v_memory_mb)::DECIMAL / GREATEST(vm.memory_mb, v_memory_mb)) * 0.4 +
        (1 - ABS(vm.primary_disk_gb - v_disk_gb)::DECIMAL / GREATEST(vm.primary_disk_gb, v_disk_gb)) * 0.2
    ) >= p_similarity_threshold
    ORDER BY similarity DESC;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- MONITORING AND ALERTING
-- =====================================================

-- View for resource utilization by provider
CREATE OR REPLACE VIEW v_provider_utilization AS
WITH resource_metrics AS (
    SELECT 
        pe.id as endpoint_id,
        COUNT(DISTINCT r.id) as total_resources,
        COUNT(DISTINCT r.id) FILTER (WHERE r.status = 'running') as running_resources,
        COUNT(DISTINCT vm.resource_id) as total_vms,
        SUM(vm.cpu_cores) FILTER (WHERE r.status = 'running') as active_vcpus,
        SUM(vm.memory_mb) FILTER (WHERE r.status = 'running') as active_memory_mb,
        COUNT(DISTINCT sv.resource_id) as total_volumes,
        SUM(sv.size_gb) as total_storage_gb
    FROM provider_endpoints pe
    LEFT JOIN resources r ON pe.id = r.provider_endpoint_id AND r.deleted_at IS NULL
    LEFT JOIN virtual_machines vm ON r.id = vm.resource_id
    LEFT JOIN storage_volumes sv ON r.id = sv.resource_id
    GROUP BY pe.id
)
SELECT 
    pt.name as provider_type,
    p.name as provider_name,
    pe.name as endpoint_name,
    pe.region,
    rm.total_resources,
    rm.running_resources,
    rm.total_vms,
    rm.active_vcpus,
    rm.active_memory_mb / 1024.0 as active_memory_gb,
    rm.total_volumes,
    rm.total_storage_gb,
    CASE 
        WHEN rm.total_resources > 0 
        THEN ROUND(100.0 * rm.running_resources / rm.total_resources, 2)
        ELSE 0 
    END as utilization_percent
FROM provider_endpoints pe
JOIN providers p ON pe.provider_id = p.id
JOIN provider_types pt ON p.provider_type_id = pt.id
JOIN resource_metrics rm ON pe.id = rm.endpoint_id
WHERE pe.is_active = true;

-- =====================================================
-- CLEANUP AND MAINTENANCE
-- =====================================================

-- Procedure to archive old resources
CREATE OR REPLACE PROCEDURE archive_deleted_resources(
    p_days_old INTEGER DEFAULT 90
) AS $$
DECLARE
    v_archived_count INTEGER;
BEGIN
    -- Create archive tables if they don't exist
    CREATE TABLE IF NOT EXISTS archive_resources (LIKE resources INCLUDING ALL);
    CREATE TABLE IF NOT EXISTS archive_resource_attributes (LIKE resource_attributes INCLUDING ALL);
    CREATE TABLE IF NOT EXISTS archive_virtual_machines (LIKE virtual_machines INCLUDING ALL);
    
    -- Archive resources
    WITH archived AS (
        INSERT INTO archive_resources
        SELECT * FROM resources
        WHERE deleted_at IS NOT NULL 
        AND deleted_at < CURRENT_TIMESTAMP - INTERVAL '1 day' * p_days_old
        RETURNING id
    )
    SELECT COUNT(*) INTO v_archived_count FROM archived;
    
    -- Archive related data
    INSERT INTO archive_resource_attributes
    SELECT ra.* FROM resource_attributes ra
    JOIN archive_resources ar ON ra.resource_id = ar.id
    ON CONFLICT DO NOTHING;
    
    INSERT INTO archive_virtual_machines
    SELECT vm.* FROM virtual_machines vm
    JOIN archive_resources ar ON vm.resource_id = ar.id
    ON CONFLICT DO NOTHING;
    
    -- Delete from main tables
    DELETE FROM resources
    WHERE deleted_at IS NOT NULL 
    AND deleted_at < CURRENT_TIMESTAMP - INTERVAL '1 day' * p_days_old;
    
    RAISE NOTICE 'Archived % resources older than % days', v_archived_count, p_days_old;
END;
$$ LANGUAGE plpgsql;