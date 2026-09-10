package test.entities.roles;

import entities.roles.*;
import test.LoggerTestHelper;

import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

public class TankTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File logFile;

    @Before
    public void setUp() throws IOException {
        logFile = LoggerTestHelper.isolateLogger(tempFolder);
    }

    @After
    public void tearDown() {
        LoggerTestHelper.resetLogger();
    }

    @Test
    public void testShieldPassiveSummary() {
        Tank tank = new Tank(1, "Tanky", "tank", "pass", 100, 20, 10);
        
        double damage1 = tank.modifyDamageTaken(40);
        assertEquals(40.0, damage1, 0.001);

        tank.setCurrentHp(30);
        double damage2 = tank.modifyDamageTaken(40);

        assertEquals(20.0, damage2, 0.001);
        assertTrue(tank.getPassiveSummary().contains("Shield aktif: 1 kali"));
    }

    @Test
    public void testResetBattleStateResetsShieldSummary() {
        Tank tank = new Tank(1, "Tanky", "tank", "pass", 100, 20, 10);

        tank.setCurrentHp(30);
        tank.modifyDamageTaken(40);
        tank.resetBattleState();

        assertTrue(tank.getPassiveSummary().contains("Shield aktif: 0 kali"));
    }
}