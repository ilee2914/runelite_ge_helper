package com.github.ilee2.gehelper;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client for the OSRS Wiki Real-time Prices API.
 * https://oldschool.runescape.wiki/w/RuneScape:Real-time_Prices
 *
 * Caches responses for 60 seconds to respect the API's update interval.
 */
@Slf4j
@Singleton
public class WikiPriceClient
{
	private static final String BASE_URL = "https://prices.runescape.wiki/api/v1/osrs";
	private static final String USER_AGENT = "RuneLite-GE-Helper-Plugin";
	private static final long CACHE_TTL_MS = 60_000; // 60 seconds

	private final OkHttpClient httpClient;
	private final Gson gson;

	// Cache fields
	private Map<Integer, PriceData> cachedPrices = new ConcurrentHashMap<>();
	private long pricesCacheTime = 0;

	private Map<Integer, TimeseriesEntry> cached24hPrices = new ConcurrentHashMap<>();
	private long prices24hCacheTime = 0;

	private Map<Integer, ItemMapping> cachedMappings = new ConcurrentHashMap<>();
	private long mappingsCacheTime = 0;

	@Inject
	public WikiPriceClient(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/**
	 * Fetch the latest prices for all items. Returns cached data if fresh enough.
	 */
	public Map<Integer, PriceData> fetchLatestPrices()
	{
		if (System.currentTimeMillis() - pricesCacheTime < CACHE_TTL_MS && !cachedPrices.isEmpty())
		{
			return cachedPrices;
		}

		try
		{
			String json = doGet(BASE_URL + "/latest");
			if (json == null)
			{
				return cachedPrices;
			}

			JsonObject root = gson.fromJson(json, JsonObject.class);
			JsonObject data = root.getAsJsonObject("data");

			Map<Integer, PriceData> result = new ConcurrentHashMap<>();
			for (Map.Entry<String, JsonElement> entry : data.entrySet())
			{
				try
				{
					int itemId = Integer.parseInt(entry.getKey());
					PriceData priceData = gson.fromJson(entry.getValue(), PriceData.class);
					result.put(itemId, priceData);
				}
				catch (NumberFormatException e)
				{
					log.warn("Skipping invalid item ID: {}", entry.getKey());
				}
			}

			cachedPrices = result;
			pricesCacheTime = System.currentTimeMillis();
			log.debug("Fetched latest prices for {} items", result.size());
		}
		catch (Exception e)
		{
			log.error("Failed to fetch latest prices", e);
		}

		return cachedPrices;
	}

	/**
	 * Fetch the latest 24h prices for all items. Returns cached data if fresh enough.
	 */
	public Map<Integer, TimeseriesEntry> fetch24hPrices()
	{
		if (System.currentTimeMillis() - prices24hCacheTime < CACHE_TTL_MS && !cached24hPrices.isEmpty())
		{
			return cached24hPrices;
		}

		try
		{
			String json = doGet(BASE_URL + "/24h");
			if (json == null)
			{
				return cached24hPrices;
			}

			JsonObject root = gson.fromJson(json, JsonObject.class);
			JsonObject data = root.getAsJsonObject("data");

			Map<Integer, TimeseriesEntry> result = new ConcurrentHashMap<>();
			for (Map.Entry<String, JsonElement> entry : data.entrySet())
			{
				try
				{
					int itemId = Integer.parseInt(entry.getKey());
					TimeseriesEntry ts = gson.fromJson(entry.getValue(), TimeseriesEntry.class);
					result.put(itemId, ts);
				}
				catch (NumberFormatException e)
				{
					// skip
				}
			}

			cached24hPrices = result;
			prices24hCacheTime = System.currentTimeMillis();
			log.debug("Fetched 24h prices for {} items", result.size());
		}
		catch (Exception e)
		{
			log.error("Failed to fetch 24h prices", e);
		}

		return cached24hPrices;
	}

	/**
	 * Fetch item ID to name mapping. Cached for the session since this data rarely changes.
	 */
	public Map<Integer, ItemMapping> fetchItemMapping()
	{
		// Mappings change very rarely, use a longer cache (10 min)
		if (System.currentTimeMillis() - mappingsCacheTime < 600_000 && !cachedMappings.isEmpty())
		{
			return cachedMappings;
		}

		try
		{
			String json = doGet(BASE_URL + "/mapping");
			if (json == null)
			{
				return cachedMappings;
			}

			Type listType = new TypeToken<List<ItemMapping>>(){}.getType();
			List<ItemMapping> mappings = gson.fromJson(json, listType);

			Map<Integer, ItemMapping> result = new ConcurrentHashMap<>();
			for (ItemMapping mapping : mappings)
			{
				result.put(mapping.getId(), mapping);
			}

			cachedMappings = result;
			mappingsCacheTime = System.currentTimeMillis();
			log.debug("Fetched mapping for {} items", result.size());
		}
		catch (Exception e)
		{
			log.error("Failed to fetch item mapping", e);
		}

		return cachedMappings;
	}

	/**
	 * Fetch timeseries data for a specific item.
	 *
	 * @param itemId the item ID
	 * @param timestep one of "5m", "1h", "6h", "24h"
	 * @return list of timeseries entries, or empty list on failure
	 */
	public List<TimeseriesEntry> fetchTimeseries(int itemId, String timestep)
	{
		try
		{
			String url = BASE_URL + "/timeseries?timestep=" + timestep + "&id=" + itemId;
			String json = doGet(url);
			if (json == null)
			{
				return Collections.emptyList();
			}

			JsonObject root = gson.fromJson(json, JsonObject.class);
			JsonElement dataElement = root.get("data");
			if (dataElement == null)
			{
				return Collections.emptyList();
			}

			Type listType = new TypeToken<List<TimeseriesEntry>>(){}.getType();
			List<TimeseriesEntry> entries = gson.fromJson(dataElement, listType);
			return entries != null ? entries : Collections.emptyList();
		}
		catch (Exception e)
		{
			log.error("Failed to fetch timeseries for item {}", itemId, e);
			return Collections.emptyList();
		}
	}

	/**
	 * Get the item name for a given item ID using the cached mapping.
	 */
	public String getItemName(int itemId)
	{
		Map<Integer, ItemMapping> mappings = fetchItemMapping();
		ItemMapping mapping = mappings.get(itemId);
		return mapping != null ? mapping.getName() : "Item #" + itemId;
	}

	/**
	 * Search for an item by name. Looks for an exact match first (case-insensitive),
	 * then falls back to a partial match (contains).
	 */
	public ItemMapping searchItem(String search)
	{
		List<ItemMapping> results = searchItems(search, 1);
		return results.isEmpty() ? null : results.get(0);
	}

	/**
	 * Search for multiple items by name to support autocomplete.
	 * Exact matches first, then prefix matches, then contains matches.
	 */
	public List<ItemMapping> searchItems(String search, int limit)
	{
		if (search == null || search.trim().isEmpty())
		{
			return Collections.emptyList();
		}

		String lowerSearch = search.trim().toLowerCase();
		Map<Integer, ItemMapping> mappings = fetchItemMapping();
		
		List<ItemMapping> exactMatches = new ArrayList<>();
		List<ItemMapping> startsWithMatches = new ArrayList<>();
		List<ItemMapping> containsMatches = new ArrayList<>();

		for (ItemMapping mapping : mappings.values())
		{
			String lowerName = mapping.getName().toLowerCase();
			if (lowerName.equals(lowerSearch))
			{
				exactMatches.add(mapping);
			}
			else if (lowerName.startsWith(lowerSearch))
			{
				startsWithMatches.add(mapping);
			}
			else if (lowerName.contains(lowerSearch))
			{
				containsMatches.add(mapping);
			}
		}

		List<ItemMapping> results = new ArrayList<>();
		results.addAll(exactMatches);
		results.addAll(startsWithMatches);
		results.addAll(containsMatches);
		
		if (results.size() > limit)
		{
			return results.subList(0, limit);
		}
		return results;
	}

	/**
	 * Get the cached price for a specific item, or null if not available.
	 */
	public PriceData getPrice(int itemId)
	{
		return cachedPrices.get(itemId);
	}

	/**
	 * Get the cached 24h price for a specific item, or null if not available.
	 */
	public TimeseriesEntry get24hPrice(int itemId)
	{
		return cached24hPrices.get(itemId);
	}

	private String doGet(String url) throws IOException
	{
		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				log.warn("HTTP {} from {}", response.code(), url);
				return null;
			}
			return response.body() != null ? response.body().string() : null;
		}
	}
}
