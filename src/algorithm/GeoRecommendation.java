package algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import db.DBConnection;
import db.DBConnectionFactory;
import entity.Item;

public class GeoRecommendation {
	public List<Item> recommendItems(String userId, double lat, double lon) {
		//recommended items holder
		List<Item> recommendedItems = new ArrayList<>();
		//connect to mysql
		DBConnection conn = DBConnectionFactory.getConnection();
		
		try {
			// Get all favorite items
			Set<String> favoriteItemIds = conn.getFavoriteItemIds(userId);
			// Get all categories of favorite items, sort by count
			Map<String, Integer> allCategories = new HashMap<>();
			for(String itemId : favoriteItemIds) {
				Set<String> categories = conn.getCategories(itemId);
				for(String category : categories) {
					allCategories.put(category, allCategories.getOrDefault(category, 0) + 1);
				}
			}
			
			List<Entry<String, Integer>> categoryList = new ArrayList<>(allCategories.entrySet());
			Collections.sort(categoryList, new Comparator<Entry<String, Integer>>(){
				@Override
				public int compare(Entry<String, Integer> entry1, Entry<String, Integer> entry2) {
					return Integer.compare(entry2.getValue(), entry1.getValue());
				}
			});
			
			// do search based on category, filter out favorite events, sort by distance
			Set<Item> visitedItems = new HashSet<>();
			for(Entry<String, Integer> category : categoryList) {
				List<Item> items = conn.searchItems(lat, lon, category.getKey());
				List<Item> filteredItems = new ArrayList<>();
				
				for(Item item : items) {
					if(!filteredItems.contains(item) && !visitedItems.contains(item)) {
						filteredItems.add(item);
					}
				}
				
				Collections.sort(filteredItems, new Comparator<Item>() {
					@Override
					public int compare(Item o1, Item o2) {
						return Double.compare(o1.getDistance(), o2.getDistance());
					}
				});
				
				visitedItems.addAll(filteredItems);
				recommendedItems.addAll(filteredItems);
			}
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
			conn.close();
		}
		
		return recommendedItems;
	}
}
