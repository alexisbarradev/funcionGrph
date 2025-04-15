package com.graphql1;

import com.microsoft.azure.functions.*;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test class for GraphQL Azure Functions.
 */
public class FunctionTest {

    @Test
    public void testGetUsersByRoleQuery() throws Exception {
        final HttpRequestMessage<Optional<String>> req = mock(HttpRequestMessage.class);
        final Optional<String> body = Optional.of(
                "{ \"query\": \"query { getUsersByRole(role: \\\"ADMIN\\\") { id username email role } }\" }"
        );
        doReturn(body).when(req).getBody();

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(req).createResponseBuilder(any(HttpStatus.class));

        final ExecutionContext context = mock(ExecutionContext.class);
        doReturn(Logger.getGlobal()).when(context).getLogger();

        GraphQLFunctionHandler handler = new GraphQLFunctionHandler();
        HttpResponseMessage response = handler.getUsersByRoleFunction(req, context);

        assertEquals(HttpStatus.OK, response.getStatus());
        assertTrue(response.getBody().toString().contains("admin1@mail.com"));
    }

    @Test
    public void testGraphqlRouterMutationAndQuery() throws Exception {
        final HttpRequestMessage<Optional<String>> req = mock(HttpRequestMessage.class);

        // Step 1: Mutation - logRoleChange
String mutation = "{ \"query\": \"mutation { logRoleChange(userId: \\\"99\\\", username: \\\"testuser\\\", oldRole: \\\"GUEST\\\", newRole: \\\"ADMIN\\\") { id changedAt username } }\" }";
doReturn(Optional.of(mutation)).when(req).getBody();


        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(req).createResponseBuilder(any(HttpStatus.class));

        final ExecutionContext context = mock(ExecutionContext.class);
        doReturn(Logger.getGlobal()).when(context).getLogger();

        GraphQLFunctionHandler handler = new GraphQLFunctionHandler();
        HttpResponseMessage mutationResponse = handler.graphqlRouter(req, context);
        assertEquals(HttpStatus.OK, mutationResponse.getStatus());
        assertTrue(mutationResponse.getBody().toString().contains("testuser"));

        // Step 2: Query audit log
        final HttpRequestMessage<Optional<String>> queryReq = mock(HttpRequestMessage.class);
        String query = "{ \"query\": \"query { getRoleChangeAuditLog { username oldRole newRole } }\" }";
        doReturn(Optional.of(query)).when(queryReq).getBody();

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(queryReq).createResponseBuilder(any(HttpStatus.class));

        HttpResponseMessage queryResponse = handler.graphqlRouter(queryReq, context);
        assertEquals(HttpStatus.OK, queryResponse.getStatus());
        assertTrue(queryResponse.getBody().toString().contains("GUEST"));
        assertTrue(queryResponse.getBody().toString().contains("ADMIN"));
    }
}
