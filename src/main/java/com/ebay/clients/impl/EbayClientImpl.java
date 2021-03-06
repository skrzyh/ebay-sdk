package com.ebay.clients.impl;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Variant;

import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ebay.clients.models.EbayError;
import com.ebay.clients.models.ErrorResponse;
import com.ebay.exceptions.EbayErrorResponseException;
import com.ebay.exceptions.EbayException;
import com.ebay.exceptions.EbayNotFoundResponseException;
import com.ebay.identity.oauth2.token.models.UserToken;
import com.ebay.models.RequestRetryConfiguration;
import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.RetryListener;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;

public class EbayClientImpl {

	public static final String AUTHORIZATION_HEADER = "Authorization";
	public static final String OAUTH_USER_TOKEN_PREFIX = "Bearer ";

	private static final Client REST_CLIENT = ClientBuilder.newClient()
			.property(ClientProperties.CONNECT_TIMEOUT, 60000).property(ClientProperties.READ_TIMEOUT, 600000)
			.register(JacksonFeature.class);
	private static final String UTF_8_ENCODING = "utf-8";
	private static final Variant ENTITY_VARIANT = new Variant(MediaType.APPLICATION_JSON_TYPE, Locale.US,
			UTF_8_ENCODING);
	private static final long ONE = 1;
	private static final long TWO = 2;
	private static final int TOO_MANY_REQUESTS_STATUS_CODE = 429;
	private static final int USER_ERROR_ERROR_ID = 25002;
	private static final int SYSTEM_ERROR_ERROR_ID = 25001;
	private static final String RETRY_ATTEMPT_MESSAGE = "Waited %s seconds since first retry attempt. This is attempt %s. Retrying due to Response Status Code of %d and Body of:\n%s";
	private static final String RETRY_FAILED_MESSAGE = "Request retry has failed.";
	private static final Logger LOGGER = LoggerFactory.getLogger(EbayClientImpl.class);

	private final URI baseUri;
	private final UserToken userToken;
	private final RequestRetryConfiguration requestRetryConfiguration;

	public EbayClientImpl(final URI baseUri, final UserToken userToken,
			final RequestRetryConfiguration requestRetryConfiguration) {
		this.baseUri = baseUri;
		this.userToken = userToken;
		this.requestRetryConfiguration = requestRetryConfiguration;
	}

	protected WebTarget getWebTarget() {
		return REST_CLIENT.target(baseUri);
	}

	protected <T> T get(final WebTarget webTarget, final Class<T> entityType, final Status... expectedStatus) {
		final Callable<Response> responseCallable = new Callable<Response>() {
			@Override
			public Response call() throws Exception {
				Response response = webTarget.request().header(AUTHORIZATION_HEADER, getUserToken()).get();
				if (Status.UNAUTHORIZED.getStatusCode() == response.getStatus()) {
					userToken.refreshToken();
					response = webTarget.request().header(AUTHORIZATION_HEADER, getUserToken()).get();
				}
				return response;
			}
		};
		final Response response = invokeResponseCallable(responseCallable);
		return handleResponse(response, entityType, expectedStatus);
	}

	protected <T, V> V post(final WebTarget webTarget, final T object, final Class<V> entityType,
			final Status... expectedStatus) {
		final Callable<Response> responseCallable = new Callable<Response>() {
			@Override
			public Response call() throws Exception {
				final Entity<T> entity = Entity.entity(object, ENTITY_VARIANT);
				Response response = webTarget.request().header(AUTHORIZATION_HEADER, getUserToken()).post(entity);
				if (Status.UNAUTHORIZED.getStatusCode() == response.getStatus()) {
					userToken.refreshToken();
					response = webTarget.request().header(AUTHORIZATION_HEADER, getUserToken()).post(entity);
				}
				return response;
			}
		};
		final Response response = invokeResponseCallable(responseCallable);
		return handleResponse(response, entityType, expectedStatus);
	}

	protected <T> void put(final WebTarget webTarget, final T object, final Status... expectedStatus) {
		final Callable<Response> responseCallable = new Callable<Response>() {
			@Override
			public Response call() throws Exception {
				final Entity<T> entity = Entity.entity(object, ENTITY_VARIANT);
				Response response = webTarget.request().header(AUTHORIZATION_HEADER, getUserToken()).put(entity);
				if (Status.UNAUTHORIZED.getStatusCode() == response.getStatus()) {
					userToken.refreshToken();
					response = webTarget.request().header(AUTHORIZATION_HEADER, getUserToken()).put(entity);
				}
				return response;
			}
		};
		final Response response = invokeResponseCallable(responseCallable);
		handleResponse(response, expectedStatus);
	}

	protected <T> void delete(final WebTarget webTarget, final Status... expectedStatus) {
		final Callable<Response> responseCallable = new Callable<Response>() {
			@Override
			public Response call() throws Exception {
				Response response = webTarget.request().header(AUTHORIZATION_HEADER, getUserToken()).delete();
				if (Status.UNAUTHORIZED.getStatusCode() == response.getStatus()) {
					userToken.refreshToken();
					response = webTarget.request().header(AUTHORIZATION_HEADER, getUserToken()).delete();
				}
				return response;
			}
		};
		final Response response = invokeResponseCallable(responseCallable);
		handleResponse(response, expectedStatus);
	}

	private Response invokeResponseCallable(final Callable<Response> responseCallable) {
		final Retryer<Response> retryer = buildResponseRetryer();
		try {
			return retryer.call(responseCallable);
		} catch (ExecutionException | RetryException e) {
			throw new EbayException(RETRY_FAILED_MESSAGE, e);
		}
	}

	private Retryer<Response> buildResponseRetryer() {
		final RetryListener retryListener = new RetryListener() {
			@Override
			public <V> void onRetry(Attempt<V> attempt) {
				final long attemptNumber = attempt.getAttemptNumber();
				if (attemptNumber > ONE) {
					final long delaySinceFirstAttemptInMilliseconds = attempt.getDelaySinceFirstAttempt();
					final long delaySinceFirstAttemptInSeconds = TimeUnit.SECONDS
							.convert(delaySinceFirstAttemptInMilliseconds, TimeUnit.MILLISECONDS);
					final Response response = (Response) attempt.getResult();
					response.bufferEntity();
					LOGGER.warn(String.format(RETRY_ATTEMPT_MESSAGE, delaySinceFirstAttemptInSeconds, attemptNumber,
							response.getStatus(), response.readEntity(String.class)));
				}
			}
		};

		final long maximumWaitDuration = requestRetryConfiguration.getMininumWaitDuration() * TWO;
		return RetryerBuilder.<Response> newBuilder().retryIfResult(this::shouldRetryResponse)
				.withWaitStrategy(WaitStrategies.randomWait(requestRetryConfiguration.getMininumWaitDuration(),
						requestRetryConfiguration.getMininumWaitUnit(), maximumWaitDuration,
						requestRetryConfiguration.getMininumWaitUnit()))
				.withRetryListener(retryListener)
				.withStopStrategy(StopStrategies.stopAfterDelay(requestRetryConfiguration.getTimeoutDuration(),
						requestRetryConfiguration.getTimeoutUnit()))
				.build();
	}

	private boolean shouldRetryResponse(final Response response) {
		return Status.Family.SERVER_ERROR == Status.Family.familyOf(response.getStatus())
				|| TOO_MANY_REQUESTS_STATUS_CODE == response.getStatus() || shouldRetryBadRequest(response);
	}

	private boolean shouldRetryBadRequest(final Response response) {
		if (Status.BAD_REQUEST.getStatusCode() == response.getStatus()) {
			response.bufferEntity();
			final ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
			return (errorResponse != null) && errorResponse.getErrors().stream().map(EbayError::getErrorId)
					.anyMatch(errorId -> (SYSTEM_ERROR_ERROR_ID == errorId) || (USER_ERROR_ERROR_ID == errorId));
		}
		return false;
	}

	private <T> T handleResponse(final Response response, final Class<T> entityType, final Status... expectedStatus) {
		final List<Integer> expectedStatusCodes = Arrays.asList(expectedStatus).stream().map(Status::getStatusCode)
				.collect(Collectors.toList());
		if (expectedStatusCodes.contains(response.getStatus())) {
			return response.readEntity(entityType);
		} else if (Status.NOT_FOUND.getStatusCode() == response.getStatus()) {
			throw new EbayNotFoundResponseException(response);
		}
		throw new EbayErrorResponseException(response);
	}

	private void handleResponse(final Response response, final Status... expectedStatus) {
		final List<Integer> expectedStatusCodes = Arrays.asList(expectedStatus).stream().map(Status::getStatusCode)
				.collect(Collectors.toList());
		if (expectedStatusCodes.contains(response.getStatus())) {
			return;
		} else if (Status.NOT_FOUND.getStatusCode() == response.getStatus()) {
			throw new EbayNotFoundResponseException(response);
		}
		throw new EbayErrorResponseException(response);
	}

	private String getUserToken() {
		return new StringBuilder().append(OAUTH_USER_TOKEN_PREFIX).append(userToken.getToken()).toString();
	}

}
