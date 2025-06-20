package com.coffeesprout.scheduler.tag;

import java.util.Set;

/**
 * Represents a parsed tag expression that can be evaluated against a set of tags.
 * Supports boolean operators AND, OR, NOT, and parentheses for grouping.
 * 
 * Examples:
 * - "env-prod" - Simple tag match
 * - "env-prod AND client-nixz" - Both tags must be present
 * - "env-prod OR env-staging" - Either tag must be present
 * - "NOT always-on" - Tag must not be present
 * - "(env-prod OR env-staging) AND NOT always-on" - Complex expression
 * - "client-* AND env-prod" - Wildcard matching
 */
public interface TagExpression {
    
    /**
     * Evaluate this expression against a set of tags
     * @param tags The tags to evaluate against
     * @return true if the expression matches the tags
     */
    boolean evaluate(Set<String> tags);
    
    /**
     * Get a human-readable string representation of this expression
     * @return String representation
     */
    String toString();
    
    /**
     * Get all concrete tags referenced in this expression (excluding wildcards)
     * @return Set of tag names
     */
    Set<String> getReferencedTags();
    
    /**
     * Check if this expression contains any wildcard patterns
     * @return true if wildcards are present
     */
    boolean hasWildcards();
    
    /**
     * Optimize this expression by simplifying redundant operations
     * @return Optimized expression (may be the same instance if no optimization possible)
     */
    TagExpression optimize();
}