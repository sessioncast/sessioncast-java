package io.sessioncast.core;

import io.sessioncast.core.tmux.TmuxController;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionTest {

    // 환경 변수에서 토큰 읽기
    private static final String TOKEN = System.getenv("SESSIONCAST_TOKEN");
    private static final String MACHINE_ID = "sessioncast-java-test";

    @Test
    void testTmuxController() {
        TmuxController tmux = new TmuxController();

        // 세션 목록 조회
        var sessions = tmux.listSessions();
        System.out.println("Current sessions: " + sessions);
        assertNotNull(sessions);

        // 테스트 세션 생성
        String testSession = "junit-test-" + System.currentTimeMillis() % 10000;
        tmux.createSession(testSession, "/tmp");
        assertTrue(tmux.sessionExists(testSession));

        // 키 전송
        tmux.sendKeysWithEnter(testSession, "echo 'Hello from JUnit!'");

        // 잠시 대기
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}

        // 화면 캡처
        String screen = tmux.capturePane(testSession);
        System.out.println("Screen:\n" + screen);
        assertTrue(screen.contains("Hello from JUnit!"));

        // 정리
        tmux.killSession(testSession);
        assertFalse(tmux.sessionExists(testSession));

        System.out.println("TmuxController test passed!");
    }

    @Test
    void testRelayConnection() throws Exception {
        System.out.println("=== Relay Connection Test ===");

        AtomicBoolean connected = new AtomicBoolean(false);
        CountDownLatch connectLatch = new CountDownLatch(1);

        SessionCastClient client = SessionCastClient.builder()
            .relay("wss://relay.sessioncast.io/ws")
            .token(TOKEN)
            .machineId(MACHINE_ID)
            .label("JUnit Test")
            .autoStreamOnCreate(false)
            .reconnect(false)
            .build();

        client.onConnect(() -> {
            System.out.println("[EVENT] Connected!");
            connected.set(true);
            connectLatch.countDown();
        });

        client.onDisconnect(reason -> {
            System.out.println("[EVENT] Disconnected: " + reason);
        });

        client.onError(e -> {
            System.out.println("[EVENT] Error: " + e.getMessage());
        });

        // 연결
        System.out.println("Connecting...");
        client.connect();

        // 연결 대기 (최대 10초)
        boolean connectedInTime = connectLatch.await(10, TimeUnit.SECONDS);

        if (connectedInTime) {
            System.out.println("Connection successful!");
            assertTrue(client.isConnected());

            // 테스트 세션 생성
            String testSession = "relay-test-" + System.currentTimeMillis() % 10000;
            System.out.println("Creating session: " + testSession);

            client.createSession(testSession, "/tmp").get(5, TimeUnit.SECONDS);
            client.sendKeys(testSession, "echo 'Connected via Java!'", true).get(5, TimeUnit.SECONDS);

            Thread.sleep(1000);

            // 정리
            client.killSession(testSession).get(5, TimeUnit.SECONDS);
            System.out.println("Session cleaned up");
        } else {
            System.out.println("Connection timed out!");
        }

        client.close();
        System.out.println("Test complete!");

        assertTrue(connected.get(), "Should have connected to relay");
    }
}
