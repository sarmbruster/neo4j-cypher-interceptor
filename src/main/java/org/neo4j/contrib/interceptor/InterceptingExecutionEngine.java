package org.neo4j.contrib.interceptor;

import org.neo4j.cypher.internal.CommunityCompatibilityFactory;
import org.neo4j.cypher.internal.CompatibilityFactory;
import org.neo4j.cypher.internal.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.internal.GraphDatabaseCypherService;
import org.neo4j.graphdb.Result;
import org.neo4j.kernel.api.query.ExecutingQuery;
import org.neo4j.kernel.impl.query.QueryExecutionKernelException;
import org.neo4j.kernel.impl.query.TransactionalContext;
import org.neo4j.logging.LogProvider;

import java.lang.reflect.Field;
import java.util.Map;

public class InterceptingExecutionEngine extends ExecutionEngine {

    private final LogProvider logProvider;
    private Field field;

    public InterceptingExecutionEngine(GraphDatabaseCypherService queryService, LogProvider logProvider, CompatibilityFactory compatibilityFactory) {
        super(queryService, logProvider, compatibilityFactory);
        this.logProvider = logProvider;
        initializeFieldAccessViaReflection();
    }

    private void initializeFieldAccessViaReflection() {
        try {
            Class<ExecutingQuery> clazz = ExecutingQuery.class;
            field = clazz.getDeclaredField("queryText");
            field.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Result executeQuery(String query, Map<String, Object> parameters, TransactionalContext context) throws QueryExecutionKernelException {
        query = rewriteQuery(query, parameters, context);
        return super.executeQuery(query, parameters, context);
    }

    @Override
    public Result profileQuery(String query, Map<String, Object> parameters, TransactionalContext context) throws QueryExecutionKernelException {
        query = rewriteQuery(query, parameters, context);
        return super.profileQuery(query, parameters, context);
    }

    protected String rewriteQuery(String query, Map<String, Object> parameters, TransactionalContext context) {

        // strip off semicolon at the end, in case query comes in via cypher-shell
        query = query.trim();
        if (query.endsWith(";")) {
            query = query.substring(0, query.length()-1);
        }

        String rewritten = interceptQuery(query, parameters, context);

        // for a unknown reason cypher does not consider the string sent to cypher
        // engine but instead a copy being stored in current ExecutionQueryObject.
        // So we have to change this as well in a nasty ways
        modifyExecutionQueryObject(rewritten, context);

//        logProvider.getLog(InterceptingExecutionEngine.class).error("HURZ: rewritten to " + rewritten);
        return rewritten;
    }

    /**
     * transform a query string to do e.g. additional checks
     * @param query
     * @param parameters change the provided parameters if you need to
     * @param context
     * @return the modified query string to be executed
     */
    protected String interceptQuery(String query, Map<String, Object> parameters, TransactionalContext context) {
        parameters.put("limit", 10);
        return query + " limit $limit";
    }

    private void modifyExecutionQueryObject(String query, TransactionalContext context) {
        final ExecutingQuery executingQuery = context.executingQuery();
        try {
            field.set(executingQuery, query);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
