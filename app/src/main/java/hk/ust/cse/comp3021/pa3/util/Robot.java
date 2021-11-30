package hk.ust.cse.comp3021.pa3.util;

import hk.ust.cse.comp3021.pa3.model.*;
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

    public final AtomicBoolean waitForMsg = new AtomicBoolean(false);

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
        //System.out.println("Created");
                this.stopDelegation();
        running.set(true);
        Thread thread1 =  new Thread() {
            public void run() {
                while(!gameState.noGemsLeft() && running.get()) {
                    while(waitForMsg.get()){

                    }
                    waitForFx.set(0);
                    count++;
                    //System.out.println(count + " Thread id: " + Thread.currentThread() + " " + "whileloop:" + (!gameState.noGemsLeft() && running.get()));
                    try {
                        Thread.sleep(timeIntervalGenerator.next());
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
                //System.out.println(count + " Thread id: " + Thread.currentThread() + " " + "Killed " + id);

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
            //System.out.println(count + " HHH");
            return;
        }

        waitForFx.set(0);
        //FXcount++;
        var directions = new ArrayList<>(Arrays.asList(Direction.values()));
        Collections.shuffle(directions);
        Direction aliveDirection = null;
        Direction deadDirection = null;
        lock.lock();
        //System.out.println(FXcount +" " + count + " " + id);
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
        //System.out.println(count + " Thread id: " + Thread.currentThread() + " " + "Finish");
        lock.unlock();
        waitForFx.set(1);

    }

    private ArrayList<Position> closestGem() {
        ArrayList<Position> allGems = new ArrayList<Position>();
        GameBoard board = gameState.getGameBoard();
        for (int i = 0; i < board.getNumCols(); i++) {
            for (int j = 0; j < board.getNumRows(); j++) {
                if (board.getCell(j, i) instanceof EntityCell) {
                    if (board.getEntityCell(j, i).getEntity() instanceof Gem)
                        allGems.add(board.getEntityCell(j, i).getPosition());
                }
            }
        }
        return allGems;
    }
    private int closestPlayer(Position player, ArrayList<Position> gemList){
        int distance = -1;
        for (Position gem : gemList){
            var dis = (gem.col() - player.col())*(gem.col() - player.col()) + (gem.row() - player.row())*(gem.row() - player.row());
            if (distance <= 0 || dis < distance){
                distance = dis;
            }
        }
        return distance;
    }
    private boolean checkAnotherPlayer(Position Org){
        var gameCol = gameState.getGameBoard().getNumCols();
        var gameRow = gameState.getGameBoard().getNumRows();
        if (Org.row()>0) {
            Position up = new Position(Org.row() - 1, Org.col());
            if (gameState.getGameBoard().getCell(up) instanceof EntityCell) {
                if (gameState.getGameBoard().getEntityCell(up).getEntity() instanceof Player) {
                    return true;
                }
            }
        }
        if (Org.row()<gameRow - 1) {
            Position down = new Position(Org.row() + 1, Org.col());
            if (gameState.getGameBoard().getCell(down) instanceof EntityCell) {
                if (gameState.getGameBoard().getEntityCell(down).getEntity() instanceof Player) {
                    return true;
                }
            }
        }
        if (Org.col() > 0) {
            Position left = new Position(Org.row(), Org.col() - 1);
            if (gameState.getGameBoard().getCell(left) instanceof EntityCell){
                if (gameState.getGameBoard().getEntityCell(left).getEntity() instanceof Player){
                    return true;
                }
            }
        }
        if (Org.col() < gameCol - 1) {
            Position right = new Position(Org.row(), Org.col() + 1);
            if (gameState.getGameBoard().getCell(right) instanceof EntityCell){
                if (gameState.getGameBoard().getEntityCell(right).getEntity() instanceof Player){
                    return true;
                }
            }
        }
        return false;
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
            //System.out.println(count + " HHH");
            return;
        }

        waitForFx.set(0);
        //FXcount++;
        var directions = new ArrayList<>(Arrays.asList(Direction.values()));
        ArrayList<Direction> move0 = new ArrayList<Direction>();
        ArrayList<Direction> move1 = new ArrayList<Direction>();
        ArrayList<Direction> move2 = new ArrayList<Direction>();
        ArrayList<Direction> move3 = new ArrayList<Direction>();
        Direction movein = null;
        lock.lock();
        MoveResult old = null;
        var chose = new ArrayList<Direction>();
        int distance = -1;
        ArrayList<Position> listOfGem = closestGem();
        try{
            old = gameState.getMoveStack().peek();
        } catch(IndexOutOfBoundsException ignored){
        }
        for (var direction :
                directions) {
            var result = tryMove(direction);
            if (result instanceof MoveResult.Valid.Alive && !((MoveResult.Valid.Alive) result).collectedGems.isEmpty()){
                move3.add(direction);
            } else if (result instanceof MoveResult.Valid.Alive && old != null &&
                    (Objects.requireNonNull(result.newPosition).row()==((MoveResult.Valid.Alive) old).origPosition.row()) &&
                    (result.newPosition.col()==((MoveResult.Valid.Alive) old).origPosition.col())){
                move1.add(direction);
            }
            else if (result instanceof MoveResult.Valid.Alive) {
                var temp  = closestPlayer(result.newPosition, listOfGem);
                if (distance <= 0 || distance >= temp) {
                    System.out.println(direction);
                    System.out.println(distance);
                    distance = temp;
                    move2.add(direction);
                }
            } else if (result instanceof MoveResult.Valid.Dead) {
                move0.add(direction);
            }else{
                movein = direction;
            }
        }
        if (!move3.isEmpty()){
            Collections.shuffle(move3);
            System.out.println(move3);
            processor.move(move3.get(0));
        }
        else if (!move2.isEmpty()) {
            Collections.shuffle(move2);
            System.out.println(move2);
            processor.move(move2.get(0));
        } else if (!move1.isEmpty()) {
            Collections.shuffle(move1);
            System.out.println(move1);
            processor.move(move1.get(0));
        }else {
            assert gameState.getPlayer().getOwner() != null;
            if (checkAnotherPlayer(gameState.getPlayer().getOwner().getPosition())){
                assert movein != null;
                processor.move(movein);
            } else if (!move0.isEmpty()) {
                Collections.shuffle(move0);
                processor.move(move0.get(0));
            }
        }
        //System.out.println(count + " Thread id: " + Thread.currentThread() + " " + "Finish");
        lock.unlock();
        waitForFx.set(1);
    }

}
