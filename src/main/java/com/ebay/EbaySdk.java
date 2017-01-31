package com.ebay;

import java.net.URI;

import org.apache.commons.lang3.StringUtils;

import com.ebay.identity.oauth2.token.clients.TokenClient;
import com.ebay.identity.oauth2.token.models.Token;
import com.ebay.identity.oauth2.token.models.UserToken;
import com.ebay.identity.ouath2.token.clients.impl.TokenClientImpl;
import com.ebay.sell.inventory.inventoryitemgroups.clients.InventoryItemGroupClient;
import com.ebay.sell.inventory.inventoryitemgroups.clients.impl.InventoryItemGroupClientImpl;
import com.ebay.sell.inventory.inventoryitemgroups.models.InventoryItemGroup;
import com.ebay.sell.inventory.inventoryitems.clients.InventoryItemClient;
import com.ebay.sell.inventory.inventoryitems.clients.impl.InventoryItemClientImpl;
import com.ebay.sell.inventory.inventoryitems.models.InventoryItem;
import com.ebay.sell.inventory.inventoryitems.models.InventoryItems;
import com.ebay.sell.inventory.offers.clients.OfferClient;
import com.ebay.sell.inventory.offers.clients.impl.OfferClientImpl;
import com.ebay.sell.inventory.offers.models.Offer;

public class EbaySdk implements InventoryItemGroupClient, InventoryItemClient, OfferClient {

	public static final URI SANDBOX_URI = URI.create("https://api.sandbox.ebay.com");
	public static final URI PRODUCTION_URI = URI.create("https://api.ebay.com");

	private final InventoryItemClient inventoryItemClient;
	private final InventoryItemGroupClient inventoryItemGroupClient;
	private final OfferClient offerClient;

	static interface ClientIdStep {
		ClientSecretStep withClientId(final String clientId);
	}

	static interface ClientSecretStep {
		CredentialsStep withClientSecret(final String clientSecret);
	}

	static interface CredentialsStep {
		SandboxStep withRefreshToken(final String refreshToken);

		CodeStep withRuName(final String ruName);
	}

	static interface CodeStep {
		SandboxStep withCode(final String code);
	}

	static interface SandboxStep {
		BuildStep withSandbox(final boolean sandbox);

		BuildStep withBaseUri(final URI baseUri);
	}

	static interface BuildStep {
		EbaySdk build();
	}

	public static ClientIdStep newBuilder() {
		return new Steps();
	}

	@Override
	public Offer getOffer(final String offerId) {
		return offerClient.getOffer(offerId);
	}

	@Override
	public void createOffer(final Offer offer) {
		offerClient.createOffer(offer);
	}

	@Override
	public void updateOffer(final Offer offer) {
		offerClient.updateOffer(offer);
	}

	@Override
	public Offer getOfferBySku(final String sku) {
		return offerClient.getOfferBySku(sku);
	}

	@Override
	public String publishOffer(final String offerId) {
		return offerClient.publishOffer(offerId);
	}

	@Override
	public InventoryItem getInventoryItem(final String sku) {
		return inventoryItemClient.getInventoryItem(sku);
	}

	@Override
	public void updateInventoryItem(final InventoryItem inventoryItem) {
		inventoryItemClient.updateInventoryItem(inventoryItem);
	}

	@Override
	public void deleteInventoryItem(final String sku) {
		inventoryItemClient.deleteInventoryItem(sku);
	}

	@Override
	public InventoryItems getInventoryItems(final int offset, final int limit) {
		return inventoryItemClient.getInventoryItems(offset, limit);
	}

	@Override
	public InventoryItemGroup getInventoryItemGroup(final String inventoryItemGroupKey) {
		return inventoryItemGroupClient.getInventoryItemGroup(inventoryItemGroupKey);
	}

	@Override
	public void deleteInventoryItemGroup(final String inventoryItemGroupKey) {
		inventoryItemGroupClient.deleteInventoryItemGroup(inventoryItemGroupKey);
	}

	@Override
	public void updateInventoryItemGroup(final InventoryItemGroup inventoryItemGroup) {
		inventoryItemGroupClient.updateInventoryItemGroup(inventoryItemGroup);
	}

	private EbaySdk(final Steps steps) {
		this.inventoryItemClient = steps.inventoryItemClient;
		this.inventoryItemGroupClient = steps.inventoryItemGroupClient;
		this.offerClient = steps.offerClient;
	}

	private static class Steps
			implements ClientIdStep, ClientSecretStep, CredentialsStep, CodeStep, SandboxStep, BuildStep {

		private InventoryItemClient inventoryItemClient;
		private InventoryItemGroupClient inventoryItemGroupClient;
		private OfferClient offerClient;

		private String clientId;
		private String clientSecret;
		private URI baseUri;
		private String refreshToken;
		private String ruName;
		private String code;

		@Override
		public EbaySdk build() {
			final TokenClient tokenClient = new TokenClientImpl(baseUri, clientId, clientSecret);

			final UserToken userToken;
			if (StringUtils.isNotBlank(refreshToken)) {
				userToken = new UserToken(tokenClient, refreshToken);
			} else {
				final Token token = tokenClient.getAccessToken(ruName, code);
				userToken = new UserToken(tokenClient, token.getRefreshToken());
			}

			inventoryItemClient = new InventoryItemClientImpl(baseUri, userToken);
			inventoryItemGroupClient = new InventoryItemGroupClientImpl(baseUri, userToken);
			offerClient = new OfferClientImpl(baseUri, userToken);

			return new EbaySdk(this);
		}

		@Override
		public BuildStep withSandbox(final boolean sandbox) {
			this.baseUri = sandbox ? SANDBOX_URI : PRODUCTION_URI;
			return this;
		}

		@Override
		public SandboxStep withCode(final String code) {
			this.code = code;
			return this;
		}

		@Override
		public SandboxStep withRefreshToken(final String refreshToken) {
			this.refreshToken = refreshToken;
			return this;
		}

		@Override
		public CodeStep withRuName(final String ruName) {
			this.ruName = ruName;
			return this;
		}

		@Override
		public CredentialsStep withClientSecret(final String clientSecret) {
			this.clientSecret = clientSecret;
			return this;
		}

		@Override
		public ClientSecretStep withClientId(final String clientId) {
			this.clientId = clientId;
			return this;
		}

		@Override
		public BuildStep withBaseUri(URI baseUri) {
			this.baseUri = baseUri;
			return this;
		}

	}

}
