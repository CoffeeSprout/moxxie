package com.coffeesprout.api;

import com.coffeesprout.api.dto.VMResponse;
import com.coffeesprout.service.TagService;
import com.coffeesprout.service.VMService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class TagResourceTest {
    
    @InjectMock
    TagService tagService;
    
    @InjectMock
    VMService vmService;
    
    @BeforeEach
    void setUp() {
        RestAssured.basePath = "/api/v1";
    }
    
    @Test
    void testGetAllTags() {
        Set<String> mockTags = Set.of("moxxie", "client:nixz", "env:prod", "always-on");
        when(tagService.getAllUniqueTags()).thenReturn(mockTags);
        
        given()
            .when()
            .get("/tags")
            .then()
            .statusCode(200)
            .body("count", is(4))
            .body("tags", hasSize(4))
            .body("tags", hasItems("moxxie", "client:nixz", "env:prod", "always-on"));
    }
    
    @Test
    void testGetVMsByTag() {
        // Mock tag service returning VM IDs
        when(tagService.getVMsByTag("client:nixz")).thenReturn(List.of(101, 102));
        
        // Mock VM service returning full VM info
        List<VMResponse> mockVMs = List.of(
            new VMResponse(101, "nixz-web-01", "pve1", "running", 4, 8192L, 100L, 3600L, "qemu", 
                List.of("moxxie", "client:nixz"), null),
            new VMResponse(102, "nixz-db-01", "pve1", "running", 8, 16384L, 200L, 7200L, "qemu", 
                List.of("moxxie", "client:nixz", "env:prod"), null)
        );
        when(vmService.listVMs(null)).thenReturn(mockVMs);
        
        given()
            .when()
            .get("/tags/client:nixz/vms")
            .then()
            .statusCode(200)
            .body("$", hasSize(2))
            .body("[0].vmid", is(101))
            .body("[0].name", is("nixz-web-01"))
            .body("[0].tags", hasItems("client:nixz"))
            .body("[1].vmid", is(102))
            .body("[1].tags", hasItems("client:nixz", "env:prod"));
    }
    
    @Test
    void testGetVMsByTagEmpty() {
        given()
            .when()
            .get("/tags//vms")
            .then()
            .statusCode(404); // Path param is empty
    }
    
    @Test
    void testBulkAddTagsByVmIds() {
        Map<Integer, String> mockResults = Map.of(
            101, "success",
            102, "success",
            103, "error: VM not found"
        );
        
        when(tagService.bulkAddTags(eq(List.of(101, 102, 103)), any()))
            .thenReturn(mockResults);
        
        String requestBody = """
            {
                "action": "ADD",
                "tags": ["client:nixz", "env:test"]
            }
            """;
        
        given()
            .contentType(ContentType.JSON)
            .queryParam("vmIds", "101,102,103")
            .body(requestBody)
            .when()
            .post("/tags/bulk")
            .then()
            .statusCode(200)
            .body("message", containsString("Added 2 tags to 2/3 VMs successfully"))
            .body("results.101", is("success"))
            .body("results.102", is("success"))
            .body("results.103", is("error: VM not found"));
    }
    
    @Test
    void testBulkAddTagsByNamePattern() {
        when(tagService.findVMsByNamePattern("nixz-*")).thenReturn(List.of(101, 102));
        
        Map<Integer, String> mockResults = Map.of(
            101, "success",
            102, "success"
        );
        
        when(tagService.bulkAddTags(eq(List.of(101, 102)), any()))
            .thenReturn(mockResults);
        
        String requestBody = """
            {
                "action": "ADD",
                "tags": ["bulk-test"]
            }
            """;
        
        given()
            .contentType(ContentType.JSON)
            .queryParam("namePattern", "nixz-*")
            .body(requestBody)
            .when()
            .post("/tags/bulk")
            .then()
            .statusCode(200)
            .body("message", containsString("Added 1 tags to 2/2 VMs successfully"));
    }
    
    @Test
    void testBulkRemoveTags() {
        Map<Integer, String> mockResults = Map.of(
            101, "success",
            102, "success"
        );
        
        when(tagService.bulkRemoveTags(eq(List.of(101, 102)), any()))
            .thenReturn(mockResults);
        
        String requestBody = """
            {
                "action": "REMOVE",
                "tags": ["env:test"]
            }
            """;
        
        given()
            .contentType(ContentType.JSON)
            .queryParam("vmIds", "101,102")
            .body(requestBody)
            .when()
            .post("/tags/bulk")
            .then()
            .statusCode(200)
            .body("message", containsString("Removed 1 tags from 2/2 VMs successfully"));
    }
    
    @Test
    void testBulkOperationNoVmsSpecified() {
        String requestBody = """
            {
                "action": "ADD",
                "tags": ["test"]
            }
            """;
        
        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/tags/bulk")
            .then()
            .statusCode(400)
            .body("error", containsString("Either vmIds or namePattern must be provided"));
    }
    
    @Test
    void testBulkOperationNoTags() {
        String requestBody = """
            {
                "action": "ADD",
                "tags": []
            }
            """;
        
        given()
            .contentType(ContentType.JSON)
            .queryParam("vmIds", "101")
            .body(requestBody)
            .when()
            .post("/tags/bulk")
            .then()
            .statusCode(400)
            .body("error", containsString("Tags list cannot be empty"));
    }
    
    @Test
    void testBulkOperationInvalidTag() {
        String requestBody = """
            {
                "action": "ADD",
                "tags": ["valid-tag", "invalid tag with spaces"]
            }
            """;
        
        given()
            .contentType(ContentType.JSON)
            .queryParam("vmIds", "101")
            .body(requestBody)
            .when()
            .post("/tags/bulk")
            .then()
            .statusCode(400)
            .body("error", containsString("Invalid tag format"));
    }
    
    @Test
    void testBulkOperationInvalidVmId() {
        String requestBody = """
            {
                "action": "ADD",
                "tags": ["test"]
            }
            """;
        
        given()
            .contentType(ContentType.JSON)
            .queryParam("vmIds", "101,invalid,103")
            .body(requestBody)
            .when()
            .post("/tags/bulk")
            .then()
            .statusCode(400)
            .body("error", containsString("Invalid VM ID: invalid"));
    }
    
    @Test
    void testBulkOperationNoVmsMatchPattern() {
        when(tagService.findVMsByNamePattern("nomatch-*")).thenReturn(List.of());
        
        String requestBody = """
            {
                "action": "ADD",
                "tags": ["test"]
            }
            """;
        
        given()
            .contentType(ContentType.JSON)
            .queryParam("namePattern", "nomatch-*")
            .body(requestBody)
            .when()
            .post("/tags/bulk")
            .then()
            .statusCode(200)
            .body("message", containsString("No VMs found matching pattern"));
    }
}