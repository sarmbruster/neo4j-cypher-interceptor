package org.neo4j.contrib.interceptor;

import org.neo4j.cypher.internal.CommunityCompatibilityFactory;
import org.neo4j.cypher.internal.CompatibilityFactory;
import org.neo4j.cypher.internal.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.internal.GraphDatabaseCypherService;
import org.neo4j.graphdb.DependencyResolver;
import org.neo4j.helpers.Service;
import org.neo4j.kernel.GraphDatabaseQueryService;
import org.neo4j.kernel.api.KernelAPI;
import org.neo4j.kernel.impl.logging.LogService;
import org.neo4j.kernel.impl.query.QueryEngineProvider;
import org.neo4j.kernel.impl.query.QueryExecutionEngine;
import org.neo4j.kernel.impl.util.Dependencies;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.kernel.monitoring.Monitors;
import org.neo4j.logging.LogProvider;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

@Service.Implementation( QueryEngineProvider.class )
public class InterceptingQueryEngineProvider extends QueryEngineProvider {

    public InterceptingQueryEngineProvider() {
        super("intercepting-cypher");
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
        CompatibilityFactory compatibilityFactory =
                new CommunityCompatibilityFactory( queryService, kernelAPI, monitors, logProvider );

        // in case of enterprise edition, wrap compat factory into enterprise variant
        // we use reflection
        try {
            Class<CompatibilityFactory> clazz = (Class<CompatibilityFactory>) Class.forName("org.neo4j.cypher.internal.EnterpriseCompatibilityFactory");
            final Constructor constructor = clazz.getConstructor(CompatibilityFactory.class, GraphDatabaseQueryService.class, KernelAPI.class, Monitors.class, LogProvider.class);
            compatibilityFactory = (CompatibilityFactory) constructor.newInstance(compatibilityFactory, queryService, kernelAPI, monitors, logProvider );

        } catch (ClassNotFoundException e) {
            logProvider.getLog(InterceptingExecutionEngine.class).info("cannot find org.neo4j.cypher.internal.EnterpriseCompatibilityFactory, using community instead");
        } catch (NoSuchMethodException|IllegalAccessException|InstantiationException|InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        deps.satisfyDependency( compatibilityFactory );
        return new InterceptingExecutionEngine( queryService, logProvider, compatibilityFactory );
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
