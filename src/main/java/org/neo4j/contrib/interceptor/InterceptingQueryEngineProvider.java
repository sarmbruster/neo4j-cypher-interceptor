package org.neo4j.contrib.interceptor;

import org.neo4j.cypher.internal.CommunityCompatibilityFactory;
import org.neo4j.cypher.internal.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.internal.GraphDatabaseCypherService;
import org.neo4j.graphdb.DependencyResolver;
import org.neo4j.helpers.Service;
import org.neo4j.kernel.api.KernelAPI;
import org.neo4j.kernel.impl.logging.LogService;
import org.neo4j.kernel.impl.query.QueryEngineProvider;
import org.neo4j.kernel.impl.query.QueryExecutionEngine;
import org.neo4j.kernel.impl.util.Dependencies;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.kernel.monitoring.Monitors;
import org.neo4j.logging.LogProvider;

@Service.Implementation( QueryEngineProvider.class )
public class InterceptingQueryEngineProvider extends QueryEngineProvider {

    public InterceptingQueryEngineProvider() {
        super("interceptingCypher");
    }

    @Override
    protected QueryExecutionEngine createEngine(Dependencies deps, GraphDatabaseAPI graphAPI) {
        GraphDatabaseCypherService queryService = new GraphDatabaseCypherService( graphAPI );
        deps.satisfyDependency( queryService );

        DependencyResolver resolver = graphAPI.getDependencyResolver();
        LogService logService = resolver.resolveDependency( LogService.class );
        KernelAPI kernelAPI = resolver.resolveDependency( KernelAPI.class );
        Monitors monitors = resolver.resolveDependency( Monitors.class );
        LogProvider logProvider = logService.getInternalLogProvider();
        CommunityCompatibilityFactory compatibilityFactory =
                new CommunityCompatibilityFactory( queryService, kernelAPI, monitors, logProvider );

                deps.satisfyDependency( compatibilityFactory );
        return new InterceptingExecutionEngine( queryService, logProvider, compatibilityFactory );

//        EnterpriseCompatibilityFactory compatibilityFactory =
//                new EnterpriseCompatibilityFactory( inner, queryService, kernelAPI, monitors, logProvider );
//        deps.satisfyDependency( compatibilityFactory );
//        return new ExecutionEngine( queryService, logProvider, compatibilityFactory );

    }

    /**
     * provide higher prio than enterprise variant
     * @return
     */
    @Override
    protected int enginePriority() {
        return -1;
    }
}
