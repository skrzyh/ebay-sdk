package com.ebay.sell.inventory.offers.clients.impl;

import static org.junit.Assert.assertEquals;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

import org.glassfish.jersey.client.ClientProperties;
import org.junit.Ignore;
import org.junit.Test;

import com.ebay.sell.inventory.offers.clients.OfferClient;
import com.ebay.sell.inventory.offers.models.Offer;

public class OfferClientDriver {

	private final Client REST_CLIENT = ClientBuilder.newClient()
			.property(ClientProperties.CONNECT_TIMEOUT, 60000)
			.property(ClientProperties.READ_TIMEOUT, 600000);
	private static final String OAUTH_USER_TOKEN = System
			.getenv("EBAY_OAUTH_USER_TOKEN");

	private final OfferClient offerClient = new OfferClientImpl(REST_CLIENT,
			OAUTH_USER_TOKEN);

	@Test
	@Ignore
	public void givenSomeInventoryItemGroupKeyWhenRetrievingInventoryItemGroupThenReturnInventoryItemGroup()
			throws Exception {
		final String offerId = "5005317010";
		final Offer actualOffer = offerClient.getOffer(offerId);
		assertEquals(offerId, actualOffer.getOfferId());
		assertEquals("540007", actualOffer.getSku());
	}

	@Test
	public void givenSomeOfferWhenUpdatingOfferThenReturn204StatusCode()
			throws Exception {
		final String offerId = "5005317010";
		final Offer offer = offerClient.getOffer(offerId);
		offer.setListingDescription("did this update?");
		offerClient.updateOffer(offer);
	}
}
