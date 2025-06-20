package com.coffeesprout.scheduler.tag;

import org.junit.jupiter.api.Test;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TagExpressionParserTest {
    
    @Test
    void testSimpleTag() {
        TagExpression expr = TagExpressionParser.parse("env-prod");
        
        assertTrue(expr.evaluate(Set.of("env-prod", "client-nixz")));
        assertFalse(expr.evaluate(Set.of("env-dev", "client-nixz")));
        assertEquals(Set.of("env-prod"), expr.getReferencedTags());
        assertFalse(expr.hasWildcards());
    }
    
    @Test
    void testAndExpression() {
        TagExpression expr = TagExpressionParser.parse("env-prod AND client-nixz");
        
        assertTrue(expr.evaluate(Set.of("env-prod", "client-nixz", "moxxie")));
        assertFalse(expr.evaluate(Set.of("env-prod", "client-other")));
        assertFalse(expr.evaluate(Set.of("env-dev", "client-nixz")));
        assertEquals(Set.of("env-prod", "client-nixz"), expr.getReferencedTags());
    }
    
    @Test
    void testOrExpression() {
        TagExpression expr = TagExpressionParser.parse("env-prod OR env-staging");
        
        assertTrue(expr.evaluate(Set.of("env-prod")));
        assertTrue(expr.evaluate(Set.of("env-staging")));
        assertTrue(expr.evaluate(Set.of("env-prod", "env-staging")));
        assertFalse(expr.evaluate(Set.of("env-dev")));
    }
    
    @Test
    void testNotExpression() {
        TagExpression expr = TagExpressionParser.parse("NOT always-on");
        
        assertTrue(expr.evaluate(Set.of("env-prod", "maint-ok")));
        assertFalse(expr.evaluate(Set.of("env-prod", "always-on")));
        assertEquals(Set.of("always-on"), expr.getReferencedTags());
    }
    
    @Test
    void testComplexExpression() {
        TagExpression expr = TagExpressionParser.parse("(env-prod OR env-staging) AND NOT always-on");
        
        assertTrue(expr.evaluate(Set.of("env-prod", "maint-ok")));
        assertTrue(expr.evaluate(Set.of("env-staging", "client-test")));
        assertFalse(expr.evaluate(Set.of("env-prod", "always-on")));
        assertFalse(expr.evaluate(Set.of("env-dev", "maint-ok")));
        assertFalse(expr.evaluate(Set.of("env-staging", "always-on")));
    }
    
    @Test
    void testWildcardExpression() {
        TagExpression expr = TagExpressionParser.parse("client-* AND env-prod");
        
        assertTrue(expr.evaluate(Set.of("client-nixz", "env-prod")));
        assertTrue(expr.evaluate(Set.of("client-test", "env-prod")));
        assertFalse(expr.evaluate(Set.of("client-nixz", "env-dev")));
        assertFalse(expr.evaluate(Set.of("env-prod"))); // No client tag
        assertTrue(expr.hasWildcards());
    }
    
    @Test
    void testMultipleWildcards() {
        TagExpression expr = TagExpressionParser.parse("k8s-* OR location-*");
        
        assertTrue(expr.evaluate(Set.of("k8s-controlplane")));
        assertTrue(expr.evaluate(Set.of("k8s-worker")));
        assertTrue(expr.evaluate(Set.of("location-dc1")));
        assertTrue(expr.evaluate(Set.of("location-dc2", "k8s-worker")));
        assertFalse(expr.evaluate(Set.of("env-prod")));
    }
    
    @Test
    void testNestedParentheses() {
        TagExpression expr = TagExpressionParser.parse("((env-prod OR env-staging) AND (client-nixz OR client-test)) AND NOT always-on");
        
        assertTrue(expr.evaluate(Set.of("env-prod", "client-nixz")));
        assertTrue(expr.evaluate(Set.of("env-staging", "client-test")));
        assertFalse(expr.evaluate(Set.of("env-prod", "client-other")));
        assertFalse(expr.evaluate(Set.of("env-dev", "client-nixz")));
        assertFalse(expr.evaluate(Set.of("env-prod", "client-nixz", "always-on")));
    }
    
    @Test
    void testDoubleNegation() {
        TagExpression expr = TagExpressionParser.parse("NOT NOT env-prod");
        
        // Should optimize to just "env-prod"
        assertTrue(expr.evaluate(Set.of("env-prod")));
        assertFalse(expr.evaluate(Set.of("env-dev")));
        assertEquals("env-prod", expr.toString());
    }
    
    @Test
    void testCaseInsensitiveOperators() {
        TagExpression expr1 = TagExpressionParser.parse("env-prod and client-nixz");
        TagExpression expr2 = TagExpressionParser.parse("env-prod AND client-nixz");
        TagExpression expr3 = TagExpressionParser.parse("env-prod AnD client-nixz");
        
        Set<String> tags = Set.of("env-prod", "client-nixz");
        assertTrue(expr1.evaluate(tags));
        assertTrue(expr2.evaluate(tags));
        assertTrue(expr3.evaluate(tags));
    }
    
    @Test
    void testInvalidExpressions() {
        assertThrows(IllegalArgumentException.class, () -> TagExpressionParser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> TagExpressionParser.parse("AND env-prod"));
        assertThrows(IllegalArgumentException.class, () -> TagExpressionParser.parse("env-prod AND"));
        assertThrows(IllegalArgumentException.class, () -> TagExpressionParser.parse("(env-prod"));
        assertThrows(IllegalArgumentException.class, () -> TagExpressionParser.parse("env-prod)"));
        assertThrows(IllegalArgumentException.class, () -> TagExpressionParser.parse("env-prod AND AND env-dev"));
    }
    
    @Test
    void testOptimization() {
        // Test that optimizations work correctly
        TagExpression expr1 = TagExpressionParser.parse("env-prod AND (env-dev OR NOT env-dev)");
        // (env-dev OR NOT env-dev) should optimize to TRUE, so whole expression becomes env-prod
        
        TagExpression expr2 = TagExpressionParser.parse("env-prod OR (env-dev AND NOT env-dev)");
        // (env-dev AND NOT env-dev) should optimize to FALSE, so whole expression becomes env-prod
        
        assertTrue(expr1.evaluate(Set.of("env-prod")));
        assertFalse(expr1.evaluate(Set.of("env-dev")));
        
        assertTrue(expr2.evaluate(Set.of("env-prod")));
        assertFalse(expr2.evaluate(Set.of("env-dev")));
    }
    
    @Test
    void testRealWorldExpressions() {
        // Test real-world use cases
        
        // 1. All production VMs except always-on
        TagExpression expr1 = TagExpressionParser.parse("env-prod AND NOT always-on");
        assertTrue(expr1.evaluate(Set.of("env-prod", "maint-ok")));
        assertFalse(expr1.evaluate(Set.of("env-prod", "always-on")));
        
        // 2. All client VMs that can undergo maintenance
        TagExpression expr2 = TagExpressionParser.parse("client-* AND maint-ok");
        assertTrue(expr2.evaluate(Set.of("client-nixz", "maint-ok")));
        assertFalse(expr2.evaluate(Set.of("client-nixz", "always-on")));
        
        // 3. All Kubernetes nodes in production or staging
        TagExpression expr3 = TagExpressionParser.parse("k8s-* AND (env-prod OR env-staging)");
        assertTrue(expr3.evaluate(Set.of("k8s-worker", "env-prod")));
        assertTrue(expr3.evaluate(Set.of("k8s-controlplane", "env-staging")));
        assertFalse(expr3.evaluate(Set.of("k8s-worker", "env-dev")));
        
        // 4. VMs managed by Moxxie but not critical
        TagExpression expr4 = TagExpressionParser.parse("moxxie AND NOT (always-on OR env-prod)");
        assertTrue(expr4.evaluate(Set.of("moxxie", "env-dev")));
        assertFalse(expr4.evaluate(Set.of("moxxie", "always-on")));
        assertFalse(expr4.evaluate(Set.of("moxxie", "env-prod")));
    }
}