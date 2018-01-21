import com.mongodb.DuplicateKeyException;
import org.bson.Document;
import org.bson.types.ObjectId;
import twitter4j.*;
import twitter4j.api.UsersResources;
import twitter4j.conf.*;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class TweetHandler {
    private String username;
    private ArrayList<Status> statuses;
    private MongoConnection conn = new MongoConnection("twitter", "tweets");
    private int numberOfTweets;

    /**
     * Connects to the Twitter API, Downloads Status Objects, Creates Document JSON, Commits to MongoDB Cluster.
     * @param username Twitter Screen Name
     * @param numberOfTweets Quantity to Download (Max 3200)
     */
    private TweetHandler(String username, int numberOfTweets) {
        setUsername(username);
        setNumberOfTweets(numberOfTweets);
    }

    private String calculateTime(long elapsed) {
        int minutes = Math.round(elapsed / (60 * 1000F));
        int seconds = Math.round((elapsed % 60000) / 1000);
        return "Operation Completed In " + minutes + "m " + seconds + "s.";
    }

    /**
     *
     * @param screenName Twitter ScreenName to search for
     * @return true if exists, false if does not.
     * @throws TwitterException When the Twitter Service or Network is unavailable.
     */
    private boolean userExists(String screenName) throws TwitterException {
        String username = "";
        try {
            ConfigurationBuilder cb = getConfigurationBuilder();
            Twitter twitter = new TwitterFactory(cb.build()).getInstance();
            UsersResources ur = twitter.users();
            User u = ur.showUser(screenName);
            username = u.getName();
            System.out.println("User '" + u.getName() + "' found.");
            System.out.println("They have " + u.getFollowersCount() + " followers.");
        } catch (TwitterException e) {
            e.printStackTrace();
        }

        return username.length() > 0;
    }

    /**
     * Downloads Twitter User Timeline by ScreenName. Created Document Objects, Committs them to MongoDB Cluster,
     * Closes the Connection. Operations happen asynchronously.
     * @throws ExecutionException
     * @throws InterruptedException
     */
    private void handleTweets() throws ExecutionException, InterruptedException {
        long start = System.currentTimeMillis();
        CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
            try {
                userExists(username);
            } catch (TwitterException e) {
                e.printStackTrace();
            }
            System.out.println("Downloading Twitter Status Objects From " + username + "...");

            int pageno = 1;
            ConfigurationBuilder cb = getConfigurationBuilder();
            Twitter twitter = new TwitterFactory(cb.build()).getInstance();
            boolean notExceededRateLimit = true;

            while (notExceededRateLimit) {
                try {
                    Paging page = new Paging(pageno++, numberOfTweets);
                    statuses.addAll(twitter.getUserTimeline(username, page));
                    if (statuses.size() == numberOfTweets)
                        break;
                } catch (TwitterException e) {
                    RateLimitStatus rls = e.getRateLimitStatus();
                    System.out.println("Twitter Rate Limit Exceeded!");
                    int s = rls.getSecondsUntilReset();
                    System.out.println("Rate Limit Time Until Reset: " + s / 60 + " m " + s % 60 + "s.");
                    System.out.println("Rate Limit: " + rls.getLimit());
                    System.out.println("Remaining: " + rls.getRemaining());
                    notExceededRateLimit = false;
                }
            }
            System.out.println("Successfully Downloaded " + statuses.size() + " Statuses.");
            return statuses;
        }).thenApply((ArrayList<Status> statuses) -> {
            ArrayList<Document> tweetJson = new ArrayList<>(3200);
            statuses.forEach((Status status) -> {
                Document json = new Document();
                try {
                    json.append("_id", new ObjectId());
                    json.append("tweet_id", status.getId());
                    json.append("created_at", status.getCreatedAt());
                    json.append("text", status.getText());
                    json.append("user", status.getUser().getName());
                    //json.put("country", status.getPlace());
                    json.append("language", status.getLang());
                } catch (NullPointerException e) {
                    e.printStackTrace();
                } catch (DuplicateKeyException e) {
                    System.out.println("Duplicate Twitter Key!");
                }
                tweetJson.add(json);
            });
            return tweetJson;
        }).thenApply((ArrayList<Document> tweets) -> {
            //System.out.println(tweets);
            int count = 0;
            int size = tweets.size();
            for (Document obj : tweets) {
                conn.insertDocument(obj);
                count++;
                System.out.println("Committed Tweet " + count + " of " + size + " @ " + obj.get("_id"));
            }
            return tweets;
        }).thenRun(new Thread(() -> {
            //conn.printCollectionStats();
            conn.disconnect();
            long elapsed = System.currentTimeMillis() - start;
            System.out.println(calculateTime(elapsed));
        }));

        future.get();
    }

    public static void main(String args[]) throws ExecutionException, InterruptedException {
        TweetHandler sa = new TweetHandler("Scarlett_Jo", 3200);
        sa.handleTweets();
    }

    /**
     * OAuth Keys - Twitter Application
     */
    private ConfigurationBuilder getConfigurationBuilder() {
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.setOAuthConsumerKey("dMy160Q3loDNlgtcm5ug4akDW");
        cb.setOAuthConsumerSecret("QLMn6ZwxygCzkuAQ2Zpxbm9d4NsQkYubscgntYBgOpLBx5h68f");
        cb.setOAuthAccessToken("569379136-Bt5ZJGog8xjxnhL9m24JacPqvg8IaYZHHGZ4ftRL");
        cb.setOAuthAccessTokenSecret("1gETn9RdTo30uWlfyPhANyve1wfUeST9zZ5UwIkXwafPP");
        System.out.println("OAuth Token Authenticated - Connected To Twitter");
        return cb;
    }

    private void setUsername(String username) {
        this.username = username;
    }

    private void setNumberOfTweets(int numberOfTweets) {
        if (numberOfTweets <= 3200) {
            this.numberOfTweets = numberOfTweets;
            statuses = new ArrayList<>(numberOfTweets);
        } else {
            System.out.println("You cannot download more than 3200 Tweets per API call.");
        }
    }
}