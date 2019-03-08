package stoplookingatmybot;

import java.awt.geom.*; // for Point2D's
import java.util.Objects;

public class EnemyWave {
    Point2D.Double fireLocation;
    long fireTime;
    double bulletVelocity, directAngle, distanceTraveled;
    int direction;

    public EnemyWave() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnemyWave enemyWave = (EnemyWave) o;
        return fireTime == enemyWave.fireTime &&
                Double.compare(enemyWave.bulletVelocity, bulletVelocity) == 0 &&
                Double.compare(enemyWave.directAngle, directAngle) == 0 &&
                Double.compare(enemyWave.distanceTraveled, distanceTraveled) == 0 &&
                direction == enemyWave.direction &&
                fireLocation.equals(enemyWave.fireLocation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fireLocation, fireTime, bulletVelocity, directAngle, distanceTraveled, direction);
    }
}

