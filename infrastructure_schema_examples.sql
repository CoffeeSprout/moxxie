-- Example Queries for Multi-Provider Infrastructure Orchestrator
-- Demonstrates the power and flexibility of the normalized schema

-- =====================================================
-- CROSS-PROVIDER QUERIES
-- =====================================================

-- 1. Get total resources by provider and type
SELECT 
    pt.name as provider_type,
    p.name as provider_name,
    rt.name as resource_type,
    COUNT(r.id) as resource_count,
    COUNT(r.id) FILTER (WHERE r.status = 'running') as running_count,
    COUNT(r.id) FILTER (WHERE r.status = 'stopped') as stopped_count
FROM resources r
JOIN provider_endpoints pe ON r.provider_endpoint_id = pe.id
JOIN providers p ON pe.provider_id = p.id
JOIN provider_types pt ON p.provider_type_id = pt.id
JOIN resource_types rt ON r.resource_type_id = rt.id
WHERE r.deleted_at IS NULL
GROUP BY pt.name, p.name, rt.name
ORDER BY pt.name, p.name, rt.name;

-- 2. Find VMs with similar specs across all providers
WITH vm_specs AS (
    SELECT 
        r.id,
        r.name,
        pt.name as provider_type,
        p.name as provider_name,
        vm.cpu_cores,
        vm.memory_mb,
        vm.primary_disk_gb
    FROM resources r
    JOIN virtual_machines vm ON r.id = vm.resource_id
    JOIN provider_endpoints pe ON r.provider_endpoint_id = pe.id
    JOIN providers p ON pe.provider_id = p.id
    JOIN provider_types pt ON p.provider_type_id = pt.id
    WHERE r.deleted_at IS NULL
)
SELECT 
    v1.provider_type as provider1,
    v1.name as vm1,
    v2.provider_type as provider2,
    v2.name as vm2,
    v1.cpu_cores,
    v1.memory_mb,
    v1.primary_disk_gb
FROM vm_specs v1
JOIN vm_specs v2 ON 
    v1.cpu_cores = v2.cpu_cores AND
    v1.memory_mb = v2.memory_mb AND
    v1.primary_disk_gb = v2.primary_disk_gb AND
    v1.provider_type != v2.provider_type AND
    v1.id < v2.id;

-- 3. Resource distribution across datacenters
SELECT 
    l.country_code,
    l.city,
    d.name as datacenter,
    pt.name as provider_type,
    rt.name as resource_type,
    COUNT(r.id) as resource_count,
    SUM(CASE WHEN rt.name = 'vm' THEN vm.cpu_cores ELSE 0 END) as total_vcpus,
    SUM(CASE WHEN rt.name = 'vm' THEN vm.memory_mb ELSE 0 END) / 1024 as total_memory_gb
FROM resources r
JOIN resource_types rt ON r.resource_type_id = rt.id
LEFT JOIN virtual_machines vm ON r.id = vm.resource_id
JOIN datacenters d ON r.datacenter_id = d.id
JOIN locations l ON d.location_id = l.id
JOIN provider_endpoints pe ON r.provider_endpoint_id = pe.id
JOIN providers p ON pe.provider_id = p.id
JOIN provider_types pt ON p.provider_type_id = pt.id
WHERE r.deleted_at IS NULL
GROUP BY l.country_code, l.city, d.name, pt.name, rt.name
ORDER BY l.country_code, l.city, d.name;

-- =====================================================
-- PROVIDER-SPECIFIC ATTRIBUTE QUERIES
-- =====================================================

-- 4. Find all Proxmox VMs with NUMA enabled
SELECT 
    r.name,
    r.external_id,
    ra_numa.value_boolean as numa_enabled,
    ra_cpu_type.value_string as cpu_type
FROM resources r
JOIN provider_endpoints pe ON r.provider_endpoint_id = pe.id
JOIN providers p ON pe.provider_id = p.id
JOIN provider_types pt ON p.provider_type_id = pt.id
LEFT JOIN resource_attributes ra_numa ON r.id = ra_numa.resource_id
    AND ra_numa.attribute_definition_id = (
        SELECT id FROM attribute_definitions WHERE name = 'numa_enabled'
    )
LEFT JOIN resource_attributes ra_cpu_type ON r.id = ra_cpu_type.resource_id
    AND ra_cpu_type.attribute_definition_id = (
        SELECT id FROM attribute_definitions WHERE name = 'cpu_type'
    )
WHERE pt.name = 'proxmox'
    AND r.resource_type_id = (SELECT id FROM resource_types WHERE name = 'vm')
    AND ra_numa.value_boolean = true
    AND r.deleted_at IS NULL;

-- 5. Compare provider-specific attributes across providers
WITH provider_attributes AS (
    SELECT 
        pt.name as provider_type,
        rt.name as resource_type,
        ad.name as attribute_name,
        ad.display_name,
        ad.data_type,
        COUNT(DISTINCT ra.resource_id) as resources_with_attribute
    FROM resource_attributes ra
    JOIN resources r ON ra.resource_id = r.id
    JOIN resource_types rt ON r.resource_type_id = rt.id
    JOIN provider_endpoints pe ON r.provider_endpoint_id = pe.id
    JOIN providers p ON pe.provider_id = p.id
    JOIN provider_types pt ON p.provider_type_id = pt.id
    JOIN attribute_definitions ad ON ra.attribute_definition_id = ad.id
    WHERE r.deleted_at IS NULL
    GROUP BY pt.name, rt.name, ad.name, ad.display_name, ad.data_type
)
SELECT * FROM provider_attributes
ORDER BY provider_type, resource_type, attribute_name;

-- =====================================================
-- RELATIONSHIP QUERIES
-- =====================================================

-- 6. Find all VMs and their attached storage
SELECT 
    vm_res.name as vm_name,
    vm_pt.name as vm_provider,
    vol_res.name as volume_name,
    sv.size_gb,
    sv.storage_type,
    rt.name as relationship_type
FROM resource_relationships rr
JOIN relationship_types rt ON rr.relationship_type_id = rt.id
JOIN resources vm_res ON rr.parent_resource_id = vm_res.id
JOIN resources vol_res ON rr.child_resource_id = vol_res.id
JOIN virtual_machines vm ON vm_res.id = vm.resource_id
JOIN storage_volumes sv ON vol_res.id = sv.resource_id
JOIN provider_endpoints vm_pe ON vm_res.provider_endpoint_id = vm_pe.id
JOIN providers vm_p ON vm_pe.provider_id = vm_p.id
JOIN provider_types vm_pt ON vm_p.provider_type_id = vm_pt.id
WHERE rt.name = 'attached_to'
    AND vm_res.deleted_at IS NULL
    AND vol_res.deleted_at IS NULL
ORDER BY vm_res.name, vol_res.name;

-- 7. Find resource dependency chains
WITH RECURSIVE dependency_chain AS (
    -- Base case: find resources with no dependencies
    SELECT 
        r.id,
        r.name,
        rt.name as resource_type,
        0 as depth,
        ARRAY[r.id] as path,
        ARRAY[r.name] as name_path
    FROM resources r
    JOIN resource_types rt ON r.resource_type_id = rt.id
    WHERE NOT EXISTS (
        SELECT 1 FROM resource_relationships rr
        JOIN relationship_types rlt ON rr.relationship_type_id = rlt.id
        WHERE rr.child_resource_id = r.id 
        AND rlt.name = 'depends_on'
    )
    AND r.deleted_at IS NULL
    
    UNION ALL
    
    -- Recursive case: find dependent resources
    SELECT 
        child_r.id,
        child_r.name,
        child_rt.name as resource_type,
        dc.depth + 1,
        dc.path || child_r.id,
        dc.name_path || child_r.name
    FROM dependency_chain dc
    JOIN resource_relationships rr ON rr.parent_resource_id = dc.id
    JOIN relationship_types rlt ON rr.relationship_type_id = rlt.id
    JOIN resources child_r ON rr.child_resource_id = child_r.id
    JOIN resource_types child_rt ON child_r.resource_type_id = child_rt.id
    WHERE rlt.name = 'depends_on'
    AND child_r.deleted_at IS NULL
    AND NOT child_r.id = ANY(dc.path) -- Prevent cycles
)
SELECT * FROM dependency_chain
WHERE depth > 0
ORDER BY depth DESC, name_path;

-- =====================================================
-- STATE AND HISTORY QUERIES
-- =====================================================

-- 8. Resource state transitions over time
SELECT 
    date_trunc('hour', rsh.created_at) as hour,
    pt.name as provider_type,
    rsh.previous_state,
    rsh.new_state,
    COUNT(*) as transition_count
FROM resource_state_history rsh
JOIN resources r ON rsh.resource_id = r.id
JOIN provider_endpoints pe ON r.provider_endpoint_id = pe.id
JOIN providers p ON pe.provider_id = p.id
JOIN provider_types pt ON p.provider_type_id = pt.id
WHERE rsh.created_at > CURRENT_TIMESTAMP - INTERVAL '24 hours'
GROUP BY date_trunc('hour', rsh.created_at), pt.name, rsh.previous_state, rsh.new_state
ORDER BY hour DESC, provider_type, transition_count DESC;

-- 9. Most frequently changing resources
SELECT 
    r.name,
    rt.name as resource_type,
    pt.name as provider_type,
    COUNT(DISTINCT rsh.id) as state_changes,
    COUNT(DISTINCT rah.id) as attribute_changes,
    COUNT(DISTINCT ro.id) as operations,
    MAX(rsh.created_at) as last_state_change
FROM resources r
JOIN resource_types rt ON r.resource_type_id = rt.id
JOIN provider_endpoints pe ON r.provider_endpoint_id = pe.id
JOIN providers p ON pe.provider_id = p.id
JOIN provider_types pt ON p.provider_type_id = pt.id
LEFT JOIN resource_state_history rsh ON r.id = rsh.resource_id
LEFT JOIN resource_attribute_history rah ON r.id = rah.resource_id
LEFT JOIN resource_operations ro ON r.id = ro.resource_id
WHERE r.created_at > CURRENT_TIMESTAMP - INTERVAL '7 days'
GROUP BY r.id, r.name, rt.name, pt.name
HAVING COUNT(DISTINCT rsh.id) + COUNT(DISTINCT rah.id) + COUNT(DISTINCT ro.id) > 10
ORDER BY (COUNT(DISTINCT rsh.id) + COUNT(DISTINCT rah.id) + COUNT(DISTINCT ro.id)) DESC;

-- =====================================================
-- OPERATIONAL QUERIES
-- =====================================================

-- 10. Failed operations by provider and type
SELECT 
    pt.name as provider_type,
    ot.name as operation_type,
    COUNT(*) as total_operations,
    COUNT(*) FILTER (WHERE ro.status = 'failed') as failed_count,
    COUNT(*) FILTER (WHERE ro.status = 'completed') as success_count,
    ROUND(
        100.0 * COUNT(*) FILTER (WHERE ro.status = 'failed') / COUNT(*),
        2
    ) as failure_rate,
    AVG(
        EXTRACT(EPOCH FROM (ro.completed_at - ro.started_at))
    ) FILTER (WHERE ro.completed_at IS NOT NULL) as avg_duration_seconds
FROM resource_operations ro
JOIN operation_types ot ON ro.operation_type_id = ot.id
JOIN resources r ON ro.resource_id = r.id
JOIN provider_endpoints pe ON r.provider_endpoint_id = pe.id
JOIN providers p ON pe.provider_id = p.id
JOIN provider_types pt ON p.provider_type_id = pt.id
WHERE ro.started_at > CURRENT_TIMESTAMP - INTERVAL '30 days'
GROUP BY pt.name, ot.name
HAVING COUNT(*) > 10
ORDER BY failure_rate DESC;

-- 11. Resource cost analysis (using tags and attributes)
WITH resource_costs AS (
    SELECT 
        r.id,
        r.name,
        pt.name as provider_type,
        rt.name as resource_type,
        COALESCE(
            -- Get cost from attribute if available
            (SELECT ra.value_decimal 
             FROM resource_attributes ra 
             JOIN attribute_definitions ad ON ra.attribute_definition_id = ad.id 
             WHERE ra.resource_id = r.id AND ad.name = 'hourly_cost'),
            -- Otherwise estimate based on resource type and size
            CASE 
                WHEN rt.name = 'vm' THEN 
                    (vm.cpu_cores * 0.01 + vm.memory_mb * 0.00001) * 
                    CASE pt.name 
                        WHEN 'hetzner' THEN 1.0
                        WHEN 'proxmox' THEN 0.5
                        ELSE 0.8
                    END
                WHEN rt.name = 'storage_volume' THEN sv.size_gb * 0.0001
                ELSE 0
            END
        ) as hourly_cost
    FROM resources r
    JOIN resource_types rt ON r.resource_type_id = rt.id
    JOIN provider_endpoints pe ON r.provider_endpoint_id = pe.id
    JOIN providers p ON pe.provider_id = p.id
    JOIN provider_types pt ON p.provider_type_id = pt.id
    LEFT JOIN virtual_machines vm ON r.id = vm.resource_id
    LEFT JOIN storage_volumes sv ON r.id = sv.resource_id
    WHERE r.deleted_at IS NULL AND r.status != 'terminated'
)
SELECT 
    provider_type,
    resource_type,
    COUNT(*) as resource_count,
    SUM(hourly_cost) as total_hourly_cost,
    SUM(hourly_cost) * 24 * 30 as estimated_monthly_cost,
    AVG(hourly_cost) as avg_hourly_cost_per_resource
FROM resource_costs
GROUP BY provider_type, resource_type
ORDER BY total_hourly_cost DESC;

-- 12. Find orphaned resources (no relationships)
SELECT 
    r.id,
    r.name,
    rt.name as resource_type,
    pt.name as provider_type,
    r.created_at,
    rcs.current_state
FROM resources r
JOIN resource_types rt ON r.resource_type_id = rt.id
JOIN provider_endpoints pe ON r.provider_endpoint_id = pe.id
JOIN providers p ON pe.provider_id = p.id
JOIN provider_types pt ON p.provider_type_id = pt.id
LEFT JOIN v_resource_current_state rcs ON r.id = rcs.resource_id
WHERE r.deleted_at IS NULL
    AND NOT EXISTS (
        SELECT 1 FROM resource_relationships rr 
        WHERE rr.parent_resource_id = r.id OR rr.child_resource_id = r.id
    )
    AND r.created_at < CURRENT_TIMESTAMP - INTERVAL '7 days'
    AND rt.name IN ('vm', 'storage_volume', 'network')
ORDER BY r.created_at;