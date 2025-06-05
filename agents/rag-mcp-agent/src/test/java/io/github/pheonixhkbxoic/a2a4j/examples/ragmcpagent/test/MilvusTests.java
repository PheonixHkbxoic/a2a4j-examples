package io.github.pheonixhkbxoic.a2a4j.examples.ragmcpagent.test;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.response.QueryResp;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * @author PheonixHkbxoic
 * @date 2025/6/5 18:57
 * @desc
 */
@Slf4j
public class MilvusTests {
    static MilvusClientV2 client;
    static String collectionName = "rag_mcp_tool";

    @BeforeAll
    public static void init() {
        ConnectConfig config = ConnectConfig.builder()
                .uri("http://localhost:19530")
                .token("root:Milvus")
                .build();
        client = new MilvusClientV2(config);
    }

    @Test
    public void delete() {
        Boolean exist = client.hasCollection(HasCollectionReq.builder()
                .collectionName(collectionName)
                .build());
        if (exist) {
            client.dropCollection(DropCollectionReq.builder().collectionName(collectionName).build());
        }
    }

    @Test
    public void querySchema() {
        DescribeCollectionResp describeCollectionResp = client.describeCollection(DescribeCollectionReq.builder().collectionName(collectionName).build());
        log.info("describeCollectionResp: {}", describeCollectionResp);
    }

    @Test
    public void query() {
        QueryResp queryResp = client.query(QueryReq.builder()
                .collectionName(collectionName)
                .filter("text LIKE \"%directory%\"")
                .limit(5)
                .outputFields(List.of("id", "metadata", "text"))
                .build());
        log.info("queryResp: {}", queryResp);
        List<QueryResp.QueryResult> queryResults = queryResp.getQueryResults();
        for (QueryResp.QueryResult queryResult : queryResults) {
            log.info("tool: {}", queryResult);
        }

    }

}
