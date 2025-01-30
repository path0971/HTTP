package com.pathfinder.httpserver;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.Executors;

public class WebServer {
    // endpoint 정의
    private static final String TASK_ENDPOINT = "/task"; // 서버에 작업을 보내는 endpoint
    private static final String STATUS_ENDPOINT = "/status"; // 서버 상태를 확인하는 point

    private final int port; // 로컬 http 서버가 수신대기할 port
    private HttpServer server;  // http server

    public WebServer(int port) { // 생성자에서 port를 webserver로 전달함
        this.port = port; // 이 포트는 외부에서 내 app으로 전달되거나 설정파일에 정적으로 정의 가능
    }

    // 이 메소드는 서버를 초기화하고 endpoint를 설정 후 시작하는 초기화용 메소드
    public void startServer() {
        try {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
        // create()는 주소와 port, 백로그 크기를 param으로 사용하며, 백로그는 서버가 대기열에 보관을 허용하는 Request 개수
        // 현재는 시스템 기본값인 0으로 진행ㅇㅇ
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // http 서버를 생성한 다음 단계는 endpoint를 정의하는 것! --> Context
        HttpContext statusContext = server.createContext(STATUS_ENDPOINT);
        HttpContext taskContext = server.createContext(TASK_ENDPOINT);

        // 아래에서 status 요청에 대한 구현을 완료했으니 startServer()로 돌아와서
        // statusContext의 핸들러를 방금 구현한 메소드로 보내 구현된 핸들러와 /status 엔드포인트를 묶어주자!
        // http request가 /status에 대해 들어오면 this가 handleStatusCheckRequest()로 처리
        statusContext.setHandler(this::handleStatusCheckRequest); // 레퍼런스를 사용하면 void 메소드도 param에 사용 가능!
        // 메소드 레퍼런스는 "메소드 실행 결과"가 아니라 "메소드 자체(= 실행 가능한 객체)"를 참조하는 것!
        // 즉, handleStatusCheckRequest가 실행되는 것이 아닌 이 메소드를 나중에 실행할 수 있도록
        // 함수형 인터페이스와 매칭되면서 HttpHandler를 구현한 객체로 동작!
        // "statusContext(즉, /status 엔드포인트)에 대한 HTTP 요청을 this::handleStatusCheckRequest 메소드가 처리하도록 handler를 설정하겠다."
        taskContext.setHandler(this::handleTaskRequest); // 이제 /task 엔드포인트로 들어온 요청을 처리 가능

        // 이제 모든 엔드포인트와 그 핸들러를 설정했으니, HTTP 서버를 위해 Thread만 할당해주면 여러 요청을 단번에 처리 가능!
        server.setExecutor(Executors.newFixedThreadPool(5));
        server.start(); // 주어진 포트에서 수신을 시작하자맨...
    }

    // Request 처리 핸들러 메소드(2)
    private void handleTaskRequest(HttpExchange exchange) throws IOException {
        // 요청 메소드가 POST인지 확인
        if (!exchange.getRequestMethod().equalsIgnoreCase("post")) {
            exchange.sendResponseHeaders(405, -1); // body 길이가 -1이면 없는 것
            exchange.close();
            return;
        }

        Headers headers = exchange.getRequestHeaders();

        // "X-Test" 헤더 확인 (테스트 모드)
        if (headers.containsKey("X-Test") && headers.get("X-Test").get(0).equalsIgnoreCase("true")) {
            String dummyResponse = "123\n";
            sendResponse(dummyResponse.getBytes(), exchange);
            return;
        }

        // "X-Debug" 헤더 확인 (디버그 모드)
        boolean isDebugMode = false;
        if (headers.containsKey("X-Debug")) {
            String debugHeaderValue = headers.get("X-Debug").get(0).trim();
            System.out.println("X-Debug Header Value: " + debugHeaderValue); // 헤더 값을 로그로 출력

            if (debugHeaderValue.equalsIgnoreCase("true")) {
                isDebugMode = true;
            }
        }

        // 연산 시작 시간 기록
        long startTime = System.nanoTime();

        // 요청 본문 읽기
        byte[] requestBytes = exchange.getRequestBody().readAllBytes();
        byte[] responseBytes = calculateResponse(requestBytes);

        long finishTime = System.nanoTime();

        // X-Debug 모드라면 디버그 정보 추가
        if (isDebugMode) {
            String debugMessage = String.format("Shit the total time consumed %d ns", finishTime - startTime);
            exchange.getResponseHeaders().add("X-Debug-Info", debugMessage); // `add()` 사용
        }

        sendResponse(responseBytes, exchange);
    }


    // task의 response body용 메소드
    private byte[] calculateResponse(byte[] requestBytes) {
        // byte[]를 인수로 받아 문자열로 변환 후, 쉼표로 구분해서 숫자 목록을 얻는 메소드
        String bodyString = new String(requestBytes).trim();
        String[] stringNumbers = bodyString.split(",");

        BigInteger result = BigInteger.ONE;

        for(String number : stringNumbers) {
            BigInteger bigInteger = new BigInteger(number);
            result = result.multiply(bigInteger);
        }
        // 결과 메시지를 통해 Response Message body에 저장가능토록 다시 byte[]로 변환
        return String.format("Fucking Result is here %s\n", result).getBytes();
    }

    // Request 처리 핸들러 메소드(1)
    private void handleStatusCheckRequest(HttpExchange exchange) throws IOException {
        // 이 메소드는 HttpExchange 타입 param을 받으며, 이 param은 현재 서버와 클라이언트 사이 트랜잭션에 대한 모든 정보가 담김
        // 우선 http request 메소드가 'get'인 지 확인해서 아니라면 해당 트랜잭션을 종료하고 요청 거절
        if(!exchange.getRequestMethod().equalsIgnoreCase("get")) {
            exchange.close();
            return;
        }
        // 'get'이 맞다면 응답으로 보낼 문자열 메시지를 생성해줌
        String respondMessage = "Good Good";
        //  Response에 해당 문자열을 전송하는 메소드
        sendResponse(respondMessage.getBytes(), exchange); // 응답 메시지 Body로 전달하기 위해
        // byte[]와 트랜잭션 상태를 나타내는 HttpExchange를 인수로 받음
    }

    // 별도로 구현하여 다른 Handler에서도 재사용을 높이자.
    private void sendResponse(byte[] responseBytes, HttpExchange exchange) throws IOException {
        // 가장 먼저 필요한 것은 Http status code를 설정하는 것으로, 트랜잭션이 성공 시 200이 돼야 함
        // 2번째 param에서 Content-Length 헤더에 메시지 Body의 길이를 할당해줌
        exchange.sendResponseHeaders(200, responseBytes.length);
        // Response Body를 출력하기 위해 응답 메시지를 Stream에 적어줌
        OutputStream outputStream = exchange.getResponseBody();
        outputStream.write(responseBytes);
        // 클라이언트에 Response를 전송하기 위해 Stream을 flush()하고 닫아줌
        outputStream.flush();
        outputStream.close();

    }

    public static void main(String[] args) {
        // 서버가 수신할 기본 포트를 정의
        int serverPort = 8080;
        // 만약 실행하면서 인수로 포트를 줬다면~~
        if(args.length == 1) {
            serverPort = Integer.parseInt(args[0]);
        }

        WebServer webServer = new WebServer(serverPort);
        webServer.startServer();

        System.out.println("서버는 어디로 수신하고있겠니? " + serverPort);
    }



}
