package db.mongodb;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.Document;

import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoDatabase;

import static com.mongodb.client.model.Filters.eq;

import db.DBConnection;
import entity.Item;
import entity.Item.ItemBuilder;
import external.TicketMasterAPI;


public class MongoDBConnection implements DBConnection{
	private MongoClient mongoClient;
	private MongoDatabase db;

	public MongoDBConnection() {
		// Connects to local mongodb server.
		mongoClient = new MongoClient();
		//找到操作相对应数据库的reference
		db = mongoClient.getDatabase(MongoDBUtil.DB_NAME);
	}

	@Override
	public void close() {//把之前打开的mongo cliend关掉
		// TODO Auto-generated method stub
		if (mongoClient != null) {
			mongoClient.close();
		}
	}

	@Override
	public void setFavoriteItems(String userId, List<String> itemIds) {
		// TODO Auto-generated method stub
		//1. find the corresponding document based on userId
		//2. push user's favorite items into "favorite" field
		db.getCollection("users").updateOne(new Document("user_id", userId),
				new Document("$push", new Document("favorite", new Document("$each", itemIds))));
	}

	@Override
	public void unsetFavoriteItems(String userId, List<String> itemIds) {
		// TODO Auto-generated method stub
		db.getCollection("users").updateOne(new Document("user_id", userId), 
				new Document("$pullAll", new Document("favorite", itemIds)));
	}

	@Override
	public Set<String> getFavoriteItemIds(String userId) {
		// TODO Auto-generated method stub
		Set<String> favoriteItems = new HashSet<>();
		//According to userId, go to  "users" document to find the exact user_id first
		//then find if the corresponding document has "favorite" key(it's possible that user hasn't favor the item) 
		// favorite is the key, it maps to a list
		FindIterable<Document> iterable = db.getCollection("users").find(eq("user_id", userId));

		if (iterable.first() != null && iterable.first().containsKey("favorite")) {
			@SuppressWarnings("unchecked")
			List<String> list = (List<String>) iterable.first().get("favorite");//the return value is an object, need transfer to list
			favoriteItems.addAll(list);
		}

		return favoriteItems;
	}

	@Override
	public Set<Item> getFavoriteItems(String userId) {
		// TODO Auto-generated method stub
		Set<Item> favoriteItems = new HashSet<>();
		//1. get favorite items' id
		//2. According to the item_id, find the corresponding fields
		//3. add all above fields in step 2 to favoriteItems
		Set<String> itemIds = getFavoriteItemIds(userId);
		for (String itemId : itemIds) {
			FindIterable<Document> iterable = db.getCollection("items").find(eq("item_id", itemId));
			if (iterable.first() != null) {
				//find the corresponding document
				Document doc = iterable.first();
				
				ItemBuilder builder = new ItemBuilder();
				builder.setItemId(doc.getString("item_id"));
				builder.setName(doc.getString("name"));
				builder.setAddress(doc.getString("address"));
				builder.setUrl(doc.getString("url"));
				builder.setImageUrl(doc.getString("image_url"));
				builder.setRating(doc.getDouble("rating"));
				builder.setDistance(doc.getDouble("distance"));
				builder.setCategories(getCategories(itemId));
				
				favoriteItems.add(builder.build());
			}
			
		}

		return favoriteItems;
	}

	@Override
	public Set<String> getCategories(String itemId) {
		// TODO Auto-generated method stub
		Set<String> categories = new HashSet<>();
		FindIterable<Document> iterable = db.getCollection("items").find(eq("item_id", itemId));
		
		if (iterable.first() != null && iterable.first().containsKey("categories")) {
			@SuppressWarnings("unchecked")
			List<String> list = (List<String>) iterable.first().get("categories");
			categories.addAll(list);
		}

		return categories;
	}

	@Override
	public List<Item> searchItems(double lat, double lon, String term) {
		// TODO Auto-generated method stub
		TicketMasterAPI tmAPI = new TicketMasterAPI();
		List<Item> items = tmAPI.search(lat, lon, term);
		for (Item item : items) {
			saveItem(item);
		}
		return items;
		//需要改saveItem（），前面的都是调用TicketMasterAPI,与当前mongodb 操作无关
	}

	@Override
	public void saveItem(Item item) {
		// TODO Auto-generated method stub
		//每搜到一条，在数据库里插入一个document
		//找到一个活动，插入到item collection 里
		//插入之前，要先查找：保证之前的item已经搜索到过，而且存在于数据库里，就不需要再插入一遍
		
		//如果能找到，就存在iterable 里面，如果找不到，iterable 结果为空
		FindIterable<Document> iterable = db.getCollection("items").find(eq("item_id", item.getItemId()));
		
		if (iterable.first() == null) {
			db.getCollection("items")
					.insertOne(new Document().append("item_id", item.getItemId()).append("distance", item.getDistance())
							.append("name", item.getName()).append("address", item.getAddress())
							.append("url", item.getUrl()).append("image_url", item.getImageUrl())
							.append("rating", item.getRating()).append("categories", item.getCategories()));
		}

	}

	@Override
	public String getFullname(String userId) {
		// TODO Auto-generated method stub
		FindIterable<Document> iterable = db.getCollection("users").find(eq("user_id", userId));
		if(iterable.first() != null) {
			Document doc = iterable.first();
			return doc.getString("first_name") + " " + doc.getString("last_name");
		}
		return "";
	}

	@Override
	public boolean verifyLogin(String userId, String password) {
		// TODO Auto-generated method stub
		FindIterable<Document> iterable = db.getCollection("users").find(eq("user_id", userId));
		if(iterable.first() != null) {
			Document doc = iterable.first();
			return doc.getString("password").equals(password);
		}
		return false;
	}

}
