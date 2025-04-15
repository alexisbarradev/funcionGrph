// Archivo: GraphQLFunctionHandler.java
package com.graphql1;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class GraphQLFunctionHandler {

    private List<Map<String, Object>> fetchAllUsers() {
        return List.of(
            Map.of("id", 1, "username", "admin1", "email", "admin1@mail.com", "role", "ADMIN"),
            Map.of("id", 2, "username", "user1", "email", "user1@mail.com", "role", "USER"),
            Map.of("id", 3, "username", "admin2", "email", "admin2@mail.com", "role", "ADMIN"),
            Map.of("id", 4, "username", "guest1", "email", "guest1@mail.com", "role", "GUEST")
        );
    }

    private static final List<Map<String, Object>> auditLog = new ArrayList<>();
    private static int nextAuditId = 1;

    @FunctionName("graphqlRouter")
    public HttpResponseMessage graphqlRouter(
        @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {

        return handleGraphQL(request, context, true);
    }

    @FunctionName("getUsersByRole")
    public HttpResponseMessage getUsersByRoleFunction(
        @HttpTrigger(name = "req", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS)
        HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context) {

        return handleGraphQL(request, context, false);
    }

    private HttpResponseMessage handleGraphQL(HttpRequestMessage<Optional<String>> request, ExecutionContext context, boolean extendedSchema) {
        context.getLogger().info("GraphQL function triggered.");

        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> jsonBody = mapper.readValue(request.getBody().orElse("{}"), new TypeReference<>() {});
            String graphqlQuery = (String) jsonBody.get("query");

            String schema = extendedSchema ? getExtendedSchema() : getSimpleSchema();
            RuntimeWiring wiring = extendedSchema ? buildExtendedWiring() : buildSimpleWiring();

            TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(schema);
            GraphQLSchema graphQLSchema = new SchemaGenerator().makeExecutableSchema(typeRegistry, wiring);
            GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema).build();

            Map<String, Object> result = graphQL.execute(graphqlQuery).toSpecification();

            return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(mapper.writeValueAsString(result))
                .build();

        } catch (Exception e) {
            context.getLogger().severe("GraphQL error: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("GraphQL execution error: " + e.getMessage())
                .build();
        }
    }

    private String getSimpleSchema() {
        return "type Query { getUsersByRole(role: String!): [User] }\n" +
               "type User { id: ID username: String email: String role: String }";
    }

    private String getExtendedSchema() {
        return "type Query { getUsersByRole(role: String!): [User] getRoleChangeAuditLog: [RoleChange] }\n" +
               "type Mutation { logRoleChange(userId: ID!, username: String!, oldRole: String!, newRole: String!): RoleChange }\n" +
               "type User { id: ID username: String email: String role: String }\n" +
               "type RoleChange { id: ID userId: ID username: String oldRole: String newRole: String changedAt: String }";
    }

    private RuntimeWiring buildSimpleWiring() {
        return RuntimeWiring.newRuntimeWiring()
            .type("Query", builder -> builder.dataFetcher("getUsersByRole", env -> {
                String roleArg = env.getArgument("role");
                return fetchAllUsers().stream()
                        .filter(user -> roleArg.equalsIgnoreCase((String) user.get("role")))
                        .collect(Collectors.toList());
            }))
            .build();
    }

    private RuntimeWiring buildExtendedWiring() {
        return RuntimeWiring.newRuntimeWiring()
            .type("Query", builder -> builder
                .dataFetcher("getUsersByRole", env -> {
                    String roleArg = env.getArgument("role");
                    return fetchAllUsers().stream()
                            .filter(user -> roleArg.equalsIgnoreCase((String) user.get("role")))
                            .collect(Collectors.toList());
                })
                .dataFetcher("getRoleChangeAuditLog", env -> auditLog)
            )
            .type("Mutation", builder -> builder.dataFetcher("logRoleChange", env -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", nextAuditId++);
                entry.put("userId", env.getArgument("userId"));
                entry.put("username", env.getArgument("username"));
                entry.put("oldRole", env.getArgument("oldRole"));
                entry.put("newRole", env.getArgument("newRole"));
                entry.put("changedAt", LocalDateTime.now().toString());
                auditLog.add(entry);
                return entry;
            }))
            .build();
    }
}
