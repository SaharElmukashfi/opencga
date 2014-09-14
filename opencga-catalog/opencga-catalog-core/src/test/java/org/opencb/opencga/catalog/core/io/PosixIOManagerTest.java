package org.opencb.opencga.catalog.core.io;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opencb.opencga.lib.common.IOUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class PosixIOManagerTest {

    static PosixIOManager posixIOManager;

    @BeforeClass
    public static void setUp() throws Exception {
        System.out.println("Testing PosixIOManagerTest");
        Path path = Paths.get("/tmp").resolve("opencga");
        try {
            if (Files.exists(path)) {
                IOUtils.deleteDirectory(path);
            }
            Files.createDirectory(path);
            posixIOManager = new PosixIOManager("/tmp/opencga", true);
        }catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCreateAccount() throws Exception {
        String userId = "imedina";
        Path userPath = posixIOManager.createUser(userId);
        assertTrue(Files.exists(userPath));
        assertEquals("/tmp/opencga/users/"+userId, userPath.toString());

        posixIOManager.deleteUser(userId);
        assertFalse(Files.exists(userPath));
    }

    @Test
    public void testCreateStudy() throws Exception {
        String userId = "imedina";
        String projectId = "1000g";

        Path userPath = posixIOManager.createUser(userId);

        Path projectPath = posixIOManager.createProject(userId, projectId);
        assertTrue(Files.exists(projectPath));
        assertEquals(userPath.toString()+"/projects/"+projectId, projectPath.toString());

        Path studyPath1 = posixIOManager.createStudy(userId, "default", "phase1");
        Path studyPath = posixIOManager.createStudy(userId, projectId, "phase1");
        assertTrue(Files.exists(studyPath));
        assertEquals(projectPath.toString()+"/phase1", studyPath.toString());

//        posixIOManager.deleteStudy(userId, projectId, "phase1");
//        assertFalse(Files.exists(studyPath));
//
//        posixIOManager.deleteProject(userId, projectId);
//        assertFalse(Files.exists(projectPath));
//
//        posixIOManager.deleteUser(userId);
//        assertFalse(Files.exists(studyPath));
    }

}