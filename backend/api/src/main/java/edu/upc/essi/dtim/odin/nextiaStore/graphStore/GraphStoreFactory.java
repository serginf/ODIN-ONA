package edu.upc.essi.dtim.odin.nextiaStore.graphStore;

import edu.upc.essi.dtim.odin.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Factory class responsible for creating instances of {@link GraphStoreInterface}
 * implementations based on the specified database type ({@code dbType}) from an {@link AppConfig} object.
 * It ensures that only one instance of the selected implementation is created and returned
 * for the lifetime of the application.
 */
@Component
public class GraphStoreFactory {

    private static final Logger logger = LoggerFactory.getLogger(GraphStoreFactory.class);
    private static GraphStoreInterface instance = null;

    public GraphStoreFactory(GraphStoreJenaImpl jenaImpl) {
        instance = jenaImpl;
        logger.info("GraphStoreFactory initialised with Spring-managed GraphStoreJenaImpl");
    }

    public static GraphStoreInterface getInstance(AppConfig appConfig) {
        if (instance == null) {
            throw new IllegalStateException("GraphStoreFactory not yet initialised by Spring");
        }
        return instance;
    }
}
