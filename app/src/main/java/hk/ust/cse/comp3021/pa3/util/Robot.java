package hk.ust.cse.comp3021.pa3.util;

import hk.ust.cse.comp3021.pa3.model.Direction;
import hk.ust.cse.comp3021.pa3.model.GameState;
import hk.ust.cse.comp3021.pa3.model.MoveResult;
import hk.ust.cse.comp3021.pa3.model.Position;
import javafx.application.Platform;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The Robot is an automated worker that can delegate the movement control of a player.
 * <p>
 * It implements the {@link MoveDelegate} interface and
 * is used by {@link hk.ust.cse.comp3021.pa3.view.panes.GameControlPane#delegateControl(MoveDelegate)}.
 */
public class Robot implements MoveDelegate {
    public enum Strategy {
        Random, Smart
    }

    /**
     * A generator to get the time interval before the robot makes the next move.
     */
    public static Generator<Long> timeIntervalGenerator = TimeIntervalGenerator.everySecond();

    /**
     * e.printStackTrace();
     * The game state of thee.printStackTrace(); player that the robot delegates.
     */
    private final GameState gameState;

    /**
     * The strategy of this instance of robot.
     */
    private final Strategy strategy;

    /**
     * an atomic boolean value to keep in check when to delete a thread, added by me
     */
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final int id;

    private static int numRobots = 0;

    private static Lock lock = new ReentrantLock();

    private static final AtomicInteger nextId = new AtomicInteger(0);

    private final AtomicInteger waitForFx = new AtomicInteger(0);

    private int count = 0;
    private int FXcount = 0;

    private ArrayList<Position> old = new ArrayList<Position>();

    public Robot(GameState gameState) {
        this(gameState, Strategy.Random);
    }

    public Robot(GameState gameState, Strategy strategy) {
        this.strategy = strategy;
        this.gameState = gameState;
        this.id = numRobots;
        numRobots++;
    }

    /**
     * TODO ok Start the delegation in a new thread.
     * The delegation should run in a separate thread.
     * This method should return immediately when the thread is started.
     * <p>
     * In the delegation of the control of the player,
     * the time interval between moves should be obtained from {@link Robot#timeIntervalGenerator}.
     * That is to say, the new thread should:
     * <ol>
     *   <li>Stop all existing threads by calling {@link Robot#stopDelegation()}</li>
     *   <li>Start a new thread. And inside the thread:</li>
     *   <ul>
     *      <li>Wait for some time (obtained from {@link TimeIntervalGenerator#next()}</li>
     *      <li>Make a move, call {@link Robot#makeMoveRandomly(MoveProcessor)} or
     *      {@link Robot#makeMoveSmartly(MoveProcessor)} according to {@link Robot#strategy}</li>
     *      <li>repeat</li>
     *   </ul>
     * </ol>
     * The started thread should be able to exit when {@link Robot#stopDelegation()} is called.
     * <p>
     *
     * @param processor The processor to make movements.
     */
    @Override
    public void startDelegation(@NotNull MoveProcessor processor) {
        System.out.println("Created");
                this.stopDelegation();
        running.set(true);
        Thread thread1 =  new Thread() {
            public void run() {
                while(!gameState.noGemsLeft() && running.get()) {
                    waitForFx.set(0);
                    count++;
                    //System.out.println(count + " Thread id: " + Thread.currentThread() + " " + "whileloop:" + (!gameState.noGemsLeft() && running.get()));
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                   // System.out.println(count + " Thread id: " + Thread.currentThread() + " " + "Any gems:" + !gameState.noGemsLeft());
                    //System.out.println(count + " Thread id: " + Thread.currentThread() + " " + "been killed?:" + running.get());
                    //System.out.println(count + " Thread id: " + Thread.currentThread() + " ");
                    if (strategy == Strategy.Random)
                        Platform.runLater( () -> makeMoveRandomly(processor) );
                    else
                        Platform.runLater( () -> makeMoveSmartly(processor) );
                    while(waitForFx.get() == 0 && !gameState.noGemsLeft() && running.get()){
                    }

                    if (gameState.hasLost()){
                        stopDelegation();
                    }


                }
                System.out.println(count + " Thread id: " + Thread.currentThread() + " " + "Killed " + id);

            }
        };
        thread1.start();
    }

    /**
     * TODO ok Stop the delegations, i.e., stop the thread of this instance.
     * When this method returns, the thread must have exited already.
     */
    @Override
    public void stopDelegation() {
        running.set(false);
    }

    private MoveResult tryMove(Direction direction) {
        var player = gameState.getPlayer();
        if (player.getOwner() == null) {
            return null;
        }
        var r = gameState.getGameBoardController().tryMove(player.getOwner().getPosition(), direction, player.getId());
        return r;
    }

    /**
     * The robot moves randomly but rationally,
     * which means the robot will not move to a direction that will make the player die if there are other choices,
     * but for other non-dying directions, the robot just randomly chooses one.
     * If there is no choice but only have one dying direction to move, the robot will still choose it.
     * If there is no valid direction, i.e. can neither die nor move, the robot do not perform a move.
     * <p>
     * TODO modify this method if you need to do thread synchronization.
     *
     * @param processor The processor to make movements.
     */
    private void makeMoveRandomly(MoveProcessor processor) {
        if (!(!gameState.noGemsLeft() && running.get())){
            System.out.println(count + " HHH");
            return;
        }

        waitForFx.set(0);
        FXcount++;
        var directions = new ArrayList<>(Arrays.asList(Direction.values()));
        Collections.shuffle(directions);
        Direction aliveDirection = null;
        Direction deadDirection = null;
        lock.lock();
        System.out.println(FXcount +" " + count + " " + id);
        for (var direction :
                directions) {
            var result = tryMove(direction);
            if (result instanceof MoveResult.Valid.Alive) {
                aliveDirection = direction;
            } else if (result instanceof MoveResult.Valid.Dead) {
                deadDirection = direction;
            }
        }
        if (aliveDirection != null) {
            processor.move(aliveDirection);
        } else if (deadDirection != null) {
            processor.move(deadDirection);
        }
        System.out.println(count + " Thread id: " + Thread.currentThread() + " " + "Finish");
        lock.unlock();
        waitForFx.set(1);

    }

    /**
     * TODO implement this method
     * The robot moves with a smarter strategy compared to random.
     * This strategy is expected to beat random strategy in most of the time.
     * That is to say we will let random robot and smart robot compete with each other and repeat many (>10) times
     * (10 seconds timeout for each run).
     * You will get the grade if the robot with your implementation can win in more than half of the total runs
     * (e.g., at least 6 when total is 10).
     * <p>
     *
     * @param processor The processor to make movements.
     */
    private void makeMoveSmartly(MoveProcessor processor) {
        if (!(!gameState.noGemsLeft() && running.get())){
            System.out.println(count + " HHH");
            return;
        }

        waitForFx.set(0);
        FXcount++;
        var directions = new ArrayList<>(Arrays.asList(Direction.values()));
        Map<Direction, Integer> moves = new HashMap<>();
        lock.lock();
        MoveResult old = null;
        var chose = new ArrayList<Direction>();
        try{
            old = gameState.getMoveStack().peek();
        } catch(IndexOutOfBoundsException e){
        }
        System.out.println(FXcount +" " + count + " " + id);
        for (var direction :
                directions) {
            var result = tryMove(direction);
            if (result instanceof MoveResult.Valid.Alive && !((MoveResult.Valid.Alive) result).collectedGems.isEmpty()){
                moves.put(direction, 3);
                System.out.println("Have Gem");
            } else if (result instanceof MoveResult.Valid.Alive && old != null &&
                    (result.newPosition.row()==((MoveResult.Valid.Alive) old).origPosition.row()) &&
                    (result.newPosition.col()==((MoveResult.Valid.Alive) old).origPosition.col())){
                moves.put(direction, 1);
            }
            else if (result instanceof MoveResult.Valid.Alive) {
                moves.put(direction, 2);
            } else if (result instanceof MoveResult.Valid.Dead) {
                moves.put(direction, 0);
            } else {
                moves.put(direction, null);
            }
        }
        if (moves.containsValue(3)){
            Direction toGo = null;
            for (Direction k : directions){
                if (moves.get(k) == null)
                    continue;
                if (moves.get(k) == 3) {
                    chose.add(k);
                }
            }
            Collections.shuffle(chose);
            processor.move(chose.get(0));
        }
        else if (moves.containsValue(2)) {
            Direction toGo = null;
            for (Direction k : directions){
                if (moves.get(k) == null)
                    continue;
                if (moves.get(k) == 2) {
                    chose.add(k);

                }
            }
            Collections.shuffle(chose);
            processor.move(chose.get(0));
        } else if (moves.containsValue(1)) {
            Direction toGo = null;
            for (Direction k : directions){
                if (moves.get(k) == null)
                    continue;
                if (moves.get(k) == 1) {
                    chose.add(k);
                }
            }
            Collections.shuffle(chose);
            processor.move(chose.get(0));
        } else if (moves.containsValue(0)) {
            Direction toGo = null;
            for (Direction k : directions){
                if (moves.get(k) == null)
                    continue;
                if (moves.get(k) == 0) {
                    chose.add(k);
                }
            }
            Collections.shuffle(chose);
            processor.move(chose.get(0));
        }
        System.out.println(count + " Thread id: " + Thread.currentThread() + " " + "Finish");
        lock.unlock();
        waitForFx.set(1);
    }

}
