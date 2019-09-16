package com.manning.neo4jia.chapter06;

import org.apache.commons.lang.RandomStringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Aleksa Vukotic
 */
public class CypherTests {

    private static final Logger logger = LoggerFactory.getLogger(CypherTests.class);
    
    public static final String STORE_DIR = "/tmp/neo4j-chapter06/"+ RandomStringUtils.randomAlphanumeric(10);
    private DataCreator dataCreator;
    private GraphDatabaseService graphDb;

    @Before
    public void setup() throws IOException {
        dataCreator = new DataCreator();
        dataCreator.setStoreDir(STORE_DIR);
        dataCreator.recreateData();

        graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(STORE_DIR);

    }

    @After
    public void shutdown(){
        if (graphDb != null) {
            graphDb.shutdown();
        }
    }

    @Test
    public void find_john_has_seen() {
        ExecutionEngine engine = new ExecutionEngine(graphDb);

        String cql = "start user=node:users(name = \"John Johnson\")" +
                        "match (user)-[:HAS_SEEN]->(movie)" +
                        "return movie;";


        ExecutionResult result = engine.execute(cql);
        logger.info("Execution result:" + result.toString());
        for(Map<String,Object> row : result){
            logger.info("Row:" + row);
        }
        List<String> columns = result.columns();
        for(String column : columns){
            logger.info("Column:" + column);
            Iterator<Object> columnValues = result.columnAs(column);
            while(columnValues.hasNext()){
                logger.info("Value:" + columnValues.next());
            }

        }
    }

    @Test
    public void find_movies_johns_friends_have_seen() {
        ExecutionEngine engine = new ExecutionEngine(graphDb);

        String cql = "start john=node:users(name = \"John Johnson\") match john-[:IS_FRIEND_OF]->(user)-[:HAS_SEEN]->(movie) return movie;\n";


        ExecutionResult result = engine.execute(cql);
        logger.info("Execution result:" + result.dumpToString());
    }

    @Test
    //shown for completeness
    //this query does not return expected results - see next test for correct solution
    public void find_movies_johns_friends_have_seen_but_john_hasnt_seen_INCORRECT() {
        ExecutionEngine engine = new ExecutionEngine(graphDb);

        String cql = "start john=node:users(name = \"John Johnson\")\n" +
                "match\n" +
                "john-[:IS_FRIEND_OF]->(user)-[:HAS_SEEN]->(movie),\n" +
                "john-[r:HAS_SEEN]->(movie)\n" +
                "return movie;\n";


        ExecutionResult result = engine.execute(cql);
        logger.info("Execution result:" + result.dumpToString());
    }
    @Test
    public void find_movies_johns_friends_have_seen_but_john_hasnt_seen_CORRECT() {
        ExecutionEngine engine = new ExecutionEngine(graphDb);

        String cql = "start john=node:users(name = \"John Johnson\")\n" +
                "match john-[:IS_FRIEND_OF]->(user)-[:HAS_SEEN]->(movie) \n" +
                "optional match john-[r:HAS_SEEN]->(movie)\n" +
                "where r is null\n" +
                "return movie.name;\n";


        ExecutionResult result = engine.execute(cql);
        logger.info("Execution result:" + result.dumpToString());
    }

    @Test
    public void find_movies_johns_friends_have_seen_without_include_films_john_has_seen() {
        ExecutionEngine engine = new ExecutionEngine(graphDb);

        String cql = "start john=node:users(name = \"John Johnson\")\n" +
                "match john-[:IS_FRIEND_OF]->(user)-[:HAS_SEEN]->(movie) \n" +
                "where NOT john -[:HAS_SEEN] ->(movie) \n" +
                "return movie.name;\n";


        ExecutionResult result = engine.execute(cql);
        logger.info("Execution result:" + result.dumpToString());
    }

    @Test
    public void loading_multiple_nodes_by_ids() {
        ExecutionEngine engine = new ExecutionEngine(graphDb);

        String cql = "start user=node(1, 2)\n" +
                "match user-[:HAS_SEEN]->(movie)\n" +
                "return distinct movie;\n";


        ExecutionResult result = engine.execute(cql);
        logger.info("Execution result:" + result.dumpToString());
    }

    @Test
    public void using_index_to_lookup_the_starting_nodes_with_native_lucene_query() {
        ExecutionEngine engine = new ExecutionEngine(graphDb);

        String cql = "start john=node:users(\"name:John* AND year_of_birth:[1950 TO 1982]\") return john";

        logger.debug("cql:" + cql);

        Transaction tx = graphDb.beginTx();

        try{
            ExecutionResult result = engine.execute(cql);

            Assert.assertNotNull(result.dumpToString());
            logger.debug("Execution result:" + result.dumpToString());
            tx.success();
        }
        catch (Exception e) {
            logger.error(e.getMessage());
        }
        finally {
            tx.close();
        }
    }

    @Test
    public void using_schema_based_index_to_lookup_the_starting_nodes() {
        ExecutionEngine engine = new ExecutionEngine(graphDb);


        String cql = "MATCH (john:USER) WHERE john.name = \"John Johnson\" RETURN john";

        ExecutionResult result = engine.execute(cql);

        Assert.assertNotNull(result.dumpToString());
        logger.debug("\nExecution result:" + result.dumpToString());
    }

    @Test
    public void using_multiple_start_nodes_in_cypher() {
        ExecutionEngine engine = new ExecutionEngine(graphDb);

        String cql = "MATCH (u:USER)-[:HAS_SEEN]->(movie) WHERE u.name = \"John Jhonson\" OR u.name = \"Jack Jeffries\"  RETURN DISTINCT movie";


        ExecutionResult result = engine.execute(cql);

        Assert.assertNotNull(result.dumpToString());
        logger.debug("Execution result:" + result.dumpToString());
    }

    @Test
    public void filtering_data() {
        ExecutionEngine engine = new ExecutionEngine(graphDb);

        String cql = "MATCH (u:USER)-[:IS_FRIEND_OF]-(friend) WHERE friend.year_of_birth > 1980 RETURN friend";


        ExecutionResult result = engine.execute(cql);

        Assert.assertNotNull(result.dumpToString());
        logger.debug("Execution result:" + result.dumpToString());
    }

    @Test
    public void returning_paths() {
        ExecutionEngine engine = new ExecutionEngine(graphDb);

        String cql = "MATCH recPath = (john)-[:IS_FRIEND_OF]->(user)-[:HAS_SEEN]->(film) WHERE not (john)-[:HAS_SEEN]->(film) RETURN film.name, recPath";


        ExecutionResult result = engine.execute(cql);

        Assert.assertNotNull(result.dumpToString());
        logger.debug("Execution result:" + result.dumpToString());
    }

    /**
     * To page Cypher query results, Neo4j has three self-explanatory clauses:
     * order - Orders the full result set before paging, so that paging returns
     *         consistent result regardless of wether you're going forward or
     *         backward through the pages
     *
     * skip - Offsets the result set so you can go to a specified page
     *
     * limit - Limits the number of returned results to the page size
     *
     */
    @Test
    public void paging_results() {
        // Ej: To display only 20 movies per web page, ordered by movie name.
        // To query the graph to get such paged results, you'd use the order,
        // limit and skip clauses. The following query returns the third page (entries 21-30)

        ExecutionEngine engine = new ExecutionEngine(graphDb);

        String cql = "MATCH (john)-[:HAS_SEEN]->(film) WHERE john.name = \"John Johnson\"  RETURN film ORDER BY film.name SKIP 20 LIMIT 10";


        ExecutionResult result = engine.execute(cql);

        Assert.assertNotNull(result.dumpToString());
        logger.debug("Execution result:" + result.dumpToString());
    }



    //all other cyphe queries used throughout chapter 6 are listed in the src/test/resources/cypher_plain_text.txt file in this project

}
