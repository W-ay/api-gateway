package com.way.apigateway;

import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * @author Way
 */
@Slf4j
public class CustomGlobalFilter implements GlobalFilter, Ordered {
    public static final List<String> WHILT_LIST = Arrays.asList("127.0.0.1");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("全局过滤器");
        ServerHttpRequest request = exchange.getRequest();
        //1.请求日志
        log.info("请求标识：{}", request.getId());
        log.info("请求路径：{}", request.getURI());
        log.info("请求方法：{}", request.getMethodValue());
        log.info("请求参数：{}", request.getQueryParams());
        log.info("本地地址：{}", request.getLocalAddress());
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        String hostAddress = request.getRemoteAddress().getAddress().getHostAddress();
        log.info("请求来源地址：{}", hostAddress);

        ServerHttpResponse response = exchange.getResponse();
        HttpStatus statusCode = response.getStatusCode();
        //2.黑白名单
        if (!WHILT_LIST.contains(hostAddress)) {
            log.info("不在白名单里：：{}", hostAddress);
            return handleNoAuth(response);
        }

        //3.用户鉴权
        HttpHeaders headers = request.getHeaders();
        String accessKey = headers.getFirst("accessKey");
        String nonce = headers.getFirst("nonce");
        String timestamp = headers.getFirst("timestamp");
        String sign = headers.getFirst("sign");
        //todo 从数据库拿secretKey,
        // 从redis拿nonce
        String secretKey = "";
        HashMap<String, String> headerMap = new HashMap<>();
        headerMap.put("accessKey", accessKey);
        headerMap.put("nonce", nonce);
        headerMap.put("timestamp", timestamp);
        if (sign == null || "".equals(sign)) {
            return handleNoAuth(response);
        }

        //todo 4.检测接口是否存在

        //5.请求转发，调用模拟接口
        return handleResponse(exchange, chain);


    }

    private Mono<Void> handleResponse(ServerWebExchange exchange, GatewayFilterChain chain) {
        try {
            ServerHttpResponse originalResponse = exchange.getResponse();
            DataBufferFactory bufferFactory = originalResponse.bufferFactory();
            HttpStatus statusCode = originalResponse.getStatusCode();
            if (statusCode == HttpStatus.OK) {
                ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
                    @Override
                    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                        log.info("body instanceof Flux: {}", (body instanceof Flux));
                        if (body instanceof Flux) {
                            Flux<? extends DataBuffer> fluxBody = Flux.from(body);
                            //
                            return super.writeWith(fluxBody.map(dataBuffer -> {
                                byte[] content = new byte[dataBuffer.readableByteCount()];
                                dataBuffer.read(content);
                                DataBufferUtils.release(dataBuffer);//释放掉内存
                                // 构建日志
                                StringBuilder sb2 = new StringBuilder(200);
                                sb2.append("<--- {} {} \n");
                                List<Object> rspArgs = new ArrayList<>();
                                rspArgs.add(originalResponse.getStatusCode());
                                //rspArgs.add(requestUrl);
                                String data = new String(content, StandardCharsets.UTF_8);//data
                                sb2.append(data);
                                log.info(sb2.toString(), rspArgs.toArray());//log.info("<-- {} {}\n", originalResponse.getStatusCode(), data);

                                //6.记录日志
                                log.info("接口成功调用");
                                //todo 7.调用成功接口次数++
                                //8.调用失败
                                return bufferFactory.wrap(content);
                            }));
                        } else {
                            log.error("<--- {} 响应code异常", getStatusCode());
                        }
                        return super.writeWith(body);
                    }
                };
                return chain.filter(exchange.mutate().response(decoratedResponse).build());
            }
            //降级处理返回数据
            return chain.filter(exchange);
        } catch (Exception e) {
            log.error("网关处理异常(GlobalFilter):" + e);
            return chain.filter(exchange);
        }
    }

    private Mono<Void> handleInvokeError(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        return response.setComplete();
    }

    public Mono<Void> handleNoAuth(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}