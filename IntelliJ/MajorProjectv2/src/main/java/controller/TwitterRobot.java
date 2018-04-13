package controller;

import org.bson.Document;
import robocode.*;
import twitter.TweetSerialiser;
import java.awt.*;
import java.util.ArrayList;

public abstract class TwitterRobot extends AdvancedRobot {
    private static int skippedTurns = 0;
    private RobotController2 rc = new RobotController2();
    private static TweetSerialiser serialiser = new TweetSerialiser();
    protected static ArrayList<Document> tweets;

    /**
     * This method is called every turn in a battle round in order to provide the
     * robot status as a complete snapshot of the robot's current state at that specific time.
     * @param e Robocode StatusEvent
     */
    public void onStatus(StatusEvent e) {
        //Update Robot's Current Coordinates
        RobotStatus robotStatus = e.getStatus();
        rc.setRobotX(robotStatus.getX());
        rc.setRobotY(robotStatus.getY());

        //Update RobotObserver GUI
        updateRobotObserver();
    }

    void initialConfiguration(int id) {
        //De-Serialise Tweets & Pass Them To RobotController
        tweets = serialiser.readTweets(id);
        rc.initialiseTweets(tweets);
        rc.setUSER(tweets.get(0).get("screenName").toString());

        //Set Twitter Palette Colours
        setBodyColor(new Color(29, 202, 255));
        setGunColor(new Color(0, 172, 237));
        setRadarColor(new Color(0, 132, 180));
        setBulletColor(new Color(29, 202, 255));
        setScanColor(new Color(192, 222, 237));
    }

    /**
     * The robots main event loop. This method is called every turn and contains
     * the robots main functionality for moving, turning, scanning and firing.
     */
    @SuppressWarnings("InfiniteLoopStatement")
    void doTurn() {
        //Loop Indefinitely
        while (true) {
            //Randomise Robot Values
            rc.randomiseValues();

            //Move Ahead
            ahead(rc.getMOVE_UP());

            //Rotate Robot Body
            if (rc.getROTATE_DIRECTION() == 1) {
                turnGunRight(rc.getROTATE_GUN());
            } else {
                turnGunLeft(rc.getROTATE_GUN() * -1);
            }

            //Invalidate All rc Values
            rc.invalidateAllValues();
        }
    }

    /**
     * Called whenever the robots scanner finds another robot.
     * @param e Robocode ScannedRobotEvent
     */
    public void onScannedRobot(ScannedRobotEvent e) {
        fire(rc.getFIRE_POWER());
    }

    /**
     * Called whenever the robot collides with one of the arena walls.
     * @param e Robocode HitWallEvent
     */
    public void onHitWall(HitWallEvent e) {
        back(rc.getMOVE_DOWN());
    }

    /**
     * Called whenever the robot dies.
     * @param e Robocode DeathEvent
     */
    public void onDeath(DeathEvent e) {
        //Count time up until, then record time taken to die. Save in DB.
    }

    /**
     * Called when the robot wins the round
     * @param e Robocode WinEvent
     */
    public void onWin(WinEvent e) {
        stop();
    }

    /**
     * Called whenever the robot performs no action and skips the turn/
     * @param e Robocode SkippedTurnEvent
     */
    public void onSkippedTurn(SkippedTurnEvent e) {
        System.out.println("Doing Nothing. Skipping Turn.");
        skippedTurns++;
    }

    protected synchronized void updateRobotObserver() {
        //Overridden In Sub-Classes
    }
}
