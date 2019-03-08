package stoplookingatmybot;

import robocode.AdvancedRobot;
import robocode.HitByBulletEvent;
import robocode.HitWallEvent;
import robocode.ScannedRobotEvent;
import robocode.util.Utils;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

// API help : https://robocode.sourceforge.io/docs/robocode/robocode/Robot.html

/**
 * StarterBot - a robot by (your name here)
 */
public class StopLookingAtMyBot extends AdvancedRobot {

    public static int BINS = 47;
    public static double _surfStats[] = new double[BINS];
    public Point2D.Double _myLocation;    // our bot's location
    public Point2D.Double _enemyLocation; // enemy bot's location

    public ArrayList<EnemyWave> _enemyWaves;
    public ArrayList<Integer> _surfDirections;
    public ArrayList<Double> _surfAbsBearings;


    public static double _oppEnergy = 100.0;

    /**
     * This is a rectangle that represents an 800x600 battle field,
     * used for a simple, iterative WallSmoothing method (by PEZ).
     * if you're not familiar with WallSmoothing, the wall stick indicates
     * the amount of space we try to always have on either end of the tank
     * (extending straight out the front or back) before touching a wall.
     */
    public static Rectangle2D.Double _fieldRect = new java.awt.geom.Rectangle2D.Double(18, 18, 764, 564);
    public static double WALL_STICK = 160;


    // guess factor targeting
    public List<WaveBullet> waves = new ArrayList<>();
    public static int[] stats = new int[31]; // 31 is the number of unique GuessFactors we're using
    public int direction = 1;                // Note: this must be an odd number so we can get
                                             // GuessFactor 0 at the middle.




    /* How many times we have decided to not change direction. */
    public int sameDirectionCounter = 0;

    /* How long we should continue to move in the current direction */
    public long moveTime = 1;

    /* The direction we are moving in */
    public static int moveDirection = 1;

    /* The speed of the last bullet that hit us, used in determining how far to move before deciding to change direction again. */
    public static double lastBulletSpeed = 15.0;

    public double wallStick = 120;






    /**
     * run: StarterBot's default behavior
     */
    public void run() {
        // Initialization of the robot should be put here

        this._enemyWaves = new ArrayList<>();
        this._surfDirections = new ArrayList<>();
        this._surfAbsBearings = new ArrayList<>();

        this.setAdjustGunForRobotTurn(true);
        this.setAdjustRadarForGunTurn(true);

        do {
            if (getRadarTurnRemaining() == 0.0) {
                setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            }
            execute();
        } while (true);
    }

    private void getWeird(ScannedRobotEvent e) {
        /* For effect only, doing this every turn could cause seizures. This makes it change every 32 turns. */
        if(e.getTime() % 32 == 0) {
            /* Set some crazy colors! */
            setBodyColor(new Color((float)Math.random(),(float)Math.random(),(float)Math.random()));
            setGunColor(new Color((float)Math.random(),(float)Math.random(),(float)Math.random()));
            setRadarColor(new Color((float)Math.random(),(float)Math.random(),(float)Math.random()));
            setBulletColor(new Color((float)Math.random(),(float)Math.random(),(float)Math.random()));
            setScanColor(new Color((float)Math.random(),(float)Math.random(),(float)Math.random()));

            /* Change the wall stick distance, to make us even more unpredictable */
            wallStick = 120 + Math.random()*40;
        }

        double absBearing = e.getBearingRadians() + getHeadingRadians();
        double distance = e.getDistance() + (Math.random()-0.5)*5.0;

        /* Radar Turn */
        double radarTurn = Utils.normalRelativeAngle(absBearing
                // Subtract current radar heading to get turn required
                - getRadarHeadingRadians() );

        double baseScanSpan = (18.0 + 36.0*Math.random());
        // Distance we want to scan from middle of enemy to either side
        double extraTurn = Math.min(Math.atan(baseScanSpan / distance), Math.PI/4.0);
        setTurnRadarRightRadians(radarTurn + (radarTurn < 0 ? -extraTurn : extraTurn));

        /* Movement */
        if(--moveTime <= 0) {
            distance = Math.max(distance, 100 + Math.random()*50) * 1.25;
            moveTime = 50 + (long)(distance / lastBulletSpeed);

            ++sameDirectionCounter;

            /* Determine if we should change direction */
            if(Math.random() < 0.5 || sameDirectionCounter > 16) {
                moveDirection = -moveDirection;
                sameDirectionCounter = 0;
            }
        }


        /* Move perpendicular to our enemy, based on our movement direction */
        double goalDirection = absBearing-Math.PI/2.0*moveDirection;

        /* This is too clean for crazy! Add some randomness. */
        goalDirection += (Math.random()-0.5) * (Math.random()*2.0 + 1.0);

        /* Smooth around the walls, if we smooth too much, reverse direction! */
        double x = getX();
        double y = getY();
        double smooth = 0;

        /* Calculate the smoothing we would end up doing if we actually smoothed walls. */
        Rectangle2D fieldRect = new Rectangle2D.Double(18, 18, getBattleFieldWidth()-36, getBattleFieldHeight()-36);

        while (!fieldRect.contains(x+Math.sin(goalDirection)*wallStick, y+ Math.cos(goalDirection)*wallStick)) {
            /* turn a little toward enemy and try again */
            goalDirection += moveDirection*0.1;
            smooth += 0.1;
        }

        /* If we would have smoothed to much, then reverse direction. */
        /* Add && sameDirectionCounter != 0 check to make this smarter */
        if(smooth > 0.5 + Math.random()*0.125) {
            moveDirection = -moveDirection;
            sameDirectionCounter = 0;
        }

        double turn = Utils.normalRelativeAngle(goalDirection - getHeadingRadians());

        /* Adjust so we drive backwards if the turn is less to go backwards */
        if (Math.abs(turn) > Math.PI/2) {
            turn = Utils.normalRelativeAngle(turn + Math.PI);
            setBack(100);
        } else {
            setAhead(100);
        }

        setTurnRightRadians(turn);


        /* Gun */
        double bulletPower = 1.0 + Math.random()*2.0;
        double bulletSpeed = 20 - 3 * bulletPower;

        /* Aim at a random offset in the general direction the enemy is heading. */
        double enemyLatVel = e.getVelocity()*Math.sin(e.getHeadingRadians() - absBearing);
        double escapeAngle = Math.asin(8.0 / bulletSpeed);

        /* Signum produces 0 if it is not moving, meaning we will fire directly head on at an unmoving target */
        double enemyDirection = Math.signum(enemyLatVel);
        double angleOffset = escapeAngle * enemyDirection * Math.random();
        setTurnGunRightRadians(Utils.normalRelativeAngle(absBearing + angleOffset - getGunHeadingRadians()));

        /* Adding this if so it does not kill itself by firing. */
        if(getEnergy() > bulletPower) {
            setFire(bulletPower);
        }
    }


    public void onPaint(Graphics2D g) {
	// use the graphics object here to draw things to the screen.
	// used for debugging and for seeing what your bot is thinking.
    }


    /**
     * onScannedRobot: What to do when you see another robot
     */
    public void onScannedRobot(ScannedRobotEvent e) {

        getWeird(e);

        this._myLocation = new Point2D.Double(this.getX(), this.getY());

        // http://robowiki.net/wiki/Lateral_Velocity
        double lateralVelocity = this.getVelocity() * Math.sin(e.getBearingRadians());
        double absBearing = e.getBearingRadians() + this.getHeadingRadians();

        this.setTurnRadarRightRadians(Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians()) * 2);

        this._surfDirections.add(0, new Integer((lateralVelocity >= 0) ? 1 : -1));
        this._surfAbsBearings.add(0, new Double(absBearing + Math.PI));

        double bulletPower = this._oppEnergy - e.getEnergy();
        if (bulletPower < 3.01 && bulletPower > 0.09 && this._surfDirections.size() > 2) {
            EnemyWave ew = new EnemyWave();
            ew.fireTime = getTime() - 1;
            ew.bulletVelocity = this.bulletVelocity(bulletPower);
            ew.distanceTraveled = this.bulletVelocity(bulletPower);
            ew.direction = (this._surfDirections.get(2)).intValue();
            ew.directAngle = (this._surfAbsBearings.get(2)).doubleValue();
            ew.fireLocation = (Point2D.Double) this._enemyLocation.clone();

            this._enemyWaves.add(ew);
        }

        this._oppEnergy = e.getEnergy();

        // update after EnemyWave detection, because that needs the previous
        // enemy location as the source of the wave
        this._enemyLocation = project(this._myLocation, absBearing, e.getDistance());

        this.updateWaves();
        this.doSurfing();

        // gun code goes here

        // find enemy's location
        double ex = getX() + Math.sin(absBearing) * e.getDistance();
        double ey = getY() + Math.cos(absBearing) * e.getDistance();

        // lets process bullet waves
        for(int i=0; i<waves.size(); i++) {
            WaveBullet currentWave = waves.get(i);
            if(currentWave.checkHit(ex, ey, getTime())) {
                waves.remove(currentWave);
                i--;
            }
        }

        double power = Math.min(3, Math.max(0.1, 2/* some function */));
        // dont try to figure out the direction they're moving
        // if they're not moving, just use the direction we had before
        if(e.getVelocity() != 0) {
            if(Math.sin(e.getHeadingRadians()-absBearing)*e.getVelocity() <0) {
                direction = -1;
            } else {
                direction = 1;
            }
        }
        int[] currentStats = stats;
        WaveBullet newWave = new WaveBullet(getX(), getY(), absBearing, power, direction, getTime(), currentStats);
        int bestIndex = 15; // initialize it to be in the middle, guessFactor 0.
        for(int i=0; i<31; i++) {
            if(currentStats[bestIndex] < currentStats[i]) {
                bestIndex = i;
            }
        }
        // this should do the opposite of the math in the WaveBullet
        double guessFactor = (double)(bestIndex - (stats.length - 1) / 2) / ((stats.length - 1) / 2);
        double angleOffset = direction * guessFactor * newWave.maxEscapeAngle();
        double gunAdjust = Utils.normalRelativeAngle(
                absBearing - getGunHeadingRadians() + angleOffset);
        setTurnGunRightRadians(gunAdjust);
        if(getGunHeat() == 0 && gunAdjust < Math.atan2(9, e.getDistance()) && setFireBullet(power) != null) {
            waves.add(newWave);
        }
    }


    private void updateWaves() {
        ListIterator<EnemyWave> it = this._enemyWaves.listIterator();
        EnemyWave ew;
        while(it.hasNext()) {
            ew = it.next();
            ew.distanceTraveled = (getTime() - ew.fireTime) * ew.bulletVelocity;
            if (ew.distanceTraveled > this._myLocation.distance(ew.fireLocation) + 50) {
                it.remove();
            }
        }
    }


    private EnemyWave getClosestSurfableWave() {
        double closestDistance = 50000; // arbitrary big number
        EnemyWave surfWave = null;
        for (EnemyWave ew : this._enemyWaves) {
            double distance = this._myLocation.distance(ew.fireLocation) - ew.distanceTraveled;
            if (distance > ew.bulletVelocity && distance < closestDistance) {
                surfWave = ew;
                closestDistance = distance;
            }
        }
        return surfWave;
    }


    /**
     * Given the EnemyWave that the bullet was on, and the point where we were hit,
     * calculate the index into our stat array for that factor.
     */
    private static int getFactorIndex(EnemyWave ew, Point2D.Double targetLocation) {
        double offsetAngle = (absoluteBearing(ew.fireLocation, targetLocation) - ew.directAngle);
        double factor = Utils.normalRelativeAngle(offsetAngle) / maxEscapeAngle(ew.bulletVelocity) * ew.direction;

        return (int)limit(0, (factor * ((BINS - 1) / 2)) + ((BINS - 1) / 2), BINS - 1);
    }


    /**
     * Given the EnemyWave that the bullet was on, and the point where we were hit,
     * update our stat array to reflect the danger in that area.
     */
    private void logHit(EnemyWave ew, Point2D.Double targetLocation) {
        int index = getFactorIndex(ew, targetLocation);
        for (int x = 0; x < BINS; x++) {
            // for the spot bin that we were hit on, add 1;
            // for the bins next to it, add 1/2;
            // the next one, add 1/5; and so on....
            this._surfStats[x] += 1.0 / (Math.pow(index - x, 2) + 1);
        }
    }


    /**
     * onHitByBullet: What to do when you're hit by a bullet
     */
    public void onHitByBullet(HitByBulletEvent e) {
        lastBulletSpeed = e.getVelocity();

        // if the _enemyWaves collection is empty, we must have missed the
        // detection of this wave somehow.
        if (!this._enemyWaves.isEmpty()) {
            Point2D.Double hitBulletLocation = new Point2D.Double(e.getBullet().getX(), e.getBullet().getY());
            EnemyWave hitWave = null;

            // Look through the EnemyWaves, and find one that could've hit us.
            for (EnemyWave ew : this._enemyWaves) {
                if (Math.abs(ew.distanceTraveled - this._myLocation.distance(ew.fireLocation)) < 50
                        && Math.abs(this.bulletVelocity(e.getBullet().getPower()) - ew.bulletVelocity) < 0.001) {
                    hitWave = ew;
                    break;
                }
            }

            if (hitWave != null) {
                this.logHit(hitWave, hitBulletLocation);

                // we can remove this wave now, of course.
                this._enemyWaves.remove(hitWave);
            }
        }
    }


    private Point2D.Double predictPosition(EnemyWave surfWave, int direction) {
        Point2D.Double predictedPosition = (Point2D.Double) this._myLocation.clone();
        double predictedVelocity = this.getVelocity();
        double predictedHeading = this.getHeadingRadians();
        double maxTurning, moveAngle, moveDir;

        int counter = 0; // number of ticks in the future
        boolean intercepted = false;

        do { // the rest of these code comments are rozu's
            moveAngle = wallSmoothing(predictedPosition,
                    absoluteBearing(surfWave.fireLocation, predictedPosition) + (direction * (Math.PI / 2)),
                    direction)
                    - predictedHeading;
            moveDir = 1;
            if (Math.cos(moveAngle) < 0) {
                moveAngle += Math.PI;
                moveDir = -1;
            }

            moveAngle = Utils.normalRelativeAngle(moveAngle);

            // maxTurning is built in like this, you can't turn more than this in one tick
            maxTurning = Math.PI / 720d * (40d - 3d * Math.abs(predictedVelocity));
            predictedHeading = Utils.normalRelativeAngle(predictedHeading + limit(maxTurning, moveAngle, maxTurning));

            // this one is nice ;). if predictedVelocity and moveDir have
            // different signs you want to break down
            // otherwise you want to accelerate (look at the factor "2")
            predictedVelocity += (predictedVelocity * moveDir < 0 ? 2 * moveDir : moveDir);
            predictedVelocity = limit(-8, predictedVelocity, 8);

            // calculate the new predicted position
            predictedPosition = project(predictedPosition, predictedHeading, predictedVelocity);

            counter++;

            if (predictedPosition.distance(surfWave.fireLocation) < surfWave.distanceTraveled + (counter * surfWave.bulletVelocity) + surfWave.bulletVelocity) {
                intercepted = true;
            }
        } while (!intercepted && counter < 500);

        return predictedPosition;
    }


    private double checkDanger(EnemyWave surfWave, int direction) {
        int index = getFactorIndex(surfWave, predictPosition(surfWave, direction));

        return this._surfStats[index];
    }


    private void doSurfing() {
        EnemyWave surfWave = getClosestSurfableWave();
        if (surfWave == null) {
            return;
        }

        double dangerLeft = checkDanger(surfWave, -1);
        double dangerRight = checkDanger(surfWave, 1);

        double goAngle = absoluteBearing(surfWave.fireLocation, this._myLocation);
        if (dangerLeft < dangerRight) {
            goAngle = wallSmoothing(this._myLocation, goAngle - (Math.PI / 2), -1);
        } else {
            goAngle = wallSmoothing(this._myLocation, goAngle + (Math.PI / 2), 1);
        }

        setBackAsFront(this, goAngle);
    }


    /**
     * onHitWall: What to do when you hit a wall
     */
    public void onHitWall(HitWallEvent e) {
        // Replace the next line with any behavior you would like
        back(100);
//        turnRight(180);


    }


    private double wallSmoothing(Point2D.Double botLocation, double angle, int orientation) {
        while (!this._fieldRect.contains(project(botLocation, angle, WALL_STICK))) {
            angle += orientation * 0.05;
        }

        return angle;
    }

    public static Point2D.Double project(Point2D.Double sourceLocation,
                                         double angle, double length) {
        return new Point2D.Double(sourceLocation.x + Math.sin(angle) * length,
                sourceLocation.y + Math.cos(angle) * length);
    }

    private static double absoluteBearing(Point2D.Double source, Point2D.Double target) {
        return Math.atan2(target.x - source.x, target.y - source.y);
    }

    private static double limit(double min, double value, double max) {
        return Math.max(min, Math.min(value, max));
    }

    private static double bulletVelocity(double power) {
        return (20.0 - (3.0 * power));
    }

    private static double maxEscapeAngle(double velocity) {
        // http://robowiki.net/wiki/Maximum_Escape_Angle
        return Math.asin(8.0 / velocity);
    }

    private static void setBackAsFront(AdvancedRobot robot, double goAngle) {
        double angle = Utils.normalRelativeAngle(goAngle - robot.getHeadingRadians());
        if (Math.abs(angle) > (Math.PI / 2)) {
            if (angle < 0) {
                robot.setTurnRightRadians(Math.PI + angle);
            } else {
                robot.setTurnLeftRadians(Math.PI - angle);
            }
            robot.setBack(100);
        } else {
            if (angle < 0) {
                robot.setTurnLeftRadians(-1 * angle);
            } else {
                robot.setTurnRightRadians(angle);
            }
            robot.setAhead(100);
        }
    }
}
