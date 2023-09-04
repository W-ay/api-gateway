package com.way.apigateway;

import cn.hutool.json.JSON;
import com.alibaba.nacos.common.model.RestResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.way.dubbointerface.common.ErrorCode;
import com.way.dubbointerface.common.ResultUtils;
import com.way.dubbointerface.model.entity.InterfaceInfo;
import com.way.dubbointerface.model.entity.User;
import com.way.dubbointerface.service.InnerInterfaceInfoService;
import com.way.dubbointerface.service.InnerNonceService;
import com.way.dubbointerface.service.InnerUserInterfaceInfoService;
import com.way.dubbointerface.service.InnerUserService;
import com.way.dubbointerface.utils.SignUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.net.URI;
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
    public static final List<String> WHILT_LIST = Arrays.asList("127.0.0.1","0:0:0:0:0:0:0:1");
    @DubboReference
    private InnerUserService innerUserService;
    @DubboReference
    private InnerInterfaceInfoService interfaceInfoService;
    @DubboReference
    private InnerUserInterfaceInfoService innerUserInterfaceInfoService;
    @DubboReference
    private InnerNonceService innerNonceService;
    private static final String HOST = "http://localhost:8090";

    @Value("${ini.gateway.api-host}")
    private String API_HOST;
    private static final String GET_NONCE_PATH= "/api/getnonce";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse response = exchange.getResponse();
        log.info("全局过滤器");
        ServerHttpRequest request = exchange.getRequest();
        //1.请求日志
        log.info("请求标识：{}", request.getId());
        URI uri = request.getURI();
        RequestPath path = request.getPath();
        //获取随机数的单独控制
//        if (path.equals(GET_NONCE_PATH)){
//            HttpHeaders headers = request.getHeaders();
//            String accessKey = headers.getFirst("accessKey");
//            String timestamp = headers.getFirst("timestamp");
//            String sign = headers.getFirst("sign");
//            //todo 判断时间戳超时
//            if (StringUtils.isAnyBlank(accessKey, timestamp, sign)) {
//                return handleNoAuth(response);
//            }
//            User invokeUser = innerUserService.getInvokeUser(accessKey);
//            if (invokeUser == null) {
//                return handleNoAuth(response);
//            }
//            HashMap<String, String> headerMap = new HashMap<>();
//            headerMap.put("accessKey", accessKey);
//            headerMap.put("timestamp", timestamp);
//            String newSign = SignUtils.getSign(headerMap, invokeUser.getSecretKey(), null);
//            if (sign == null || !newSign.equals(sign)) {
//                return handleNoAuth(response);
//            }
//            //todo 随机数
//            return chain.filter(exchange);
//        }
//        String url = API_HOST + path;
        String method = request.getMethodValue();

        log.info("请求网关路径：{}", uri);
//        log.info("请求目的路径：{}", url);
        log.info("请求方法：{}", method);
        log.info("请求参数：{}", request.getQueryParams());
        log.info("本地地址：{}", request.getLocalAddress());
        log.info("请求体：{}", request.getBody());
        log.info("cookies：{}", request.getCookies());
        log.info("cookies：{}", request.getHeaders());

        InetSocketAddress remoteAddress = request.getRemoteAddress();
        String hostAddress = request.getRemoteAddress().getAddress().getHostAddress();
        log.info("请求来源地址：{}", hostAddress);

        //处理请求地址
        String scheme = uri.getScheme();
        String host = uri.getHost();
        String path2 = uri.getPath();
        int port = uri.getPort();
        String requestURL = scheme+"://"+host+":"+ port +path;
        if (path2.charAt(path2.length()-1) != '/'){
            requestURL += '/';
        }
        log.info("请求链接：{}",requestURL);
//        //2.黑白名单
//        if (!WHILT_LIST.contains(hostAddress)) {
//            log.info("不在白名单里：：{}", hostAddress);
//            return handleNoAuth(response);
//        }

//        //3.用户鉴权
        HttpHeaders headers = request.getHeaders();
        String accessKey = headers.getFirst("accessKey");
        String secretKey = headers.getFirst("secretKey");
//        String nonce = headers.getFirst("nonce");
//        String timestamp = headers.getFirst("timestamp");
//        String sign = headers.getFirst("sign");
//        if (StringUtils.isAnyBlank(accessKey, nonce, timestamp, sign)) {
//            return handleNoAuth(response);
//        }
        if (StringUtils.isAnyBlank(accessKey)) {
            return handleNoAuth(response);
        }
//        //从数据库拿secretKey,
        User invokeUser = innerUserService.getInvokeUser(accessKey);
        if (invokeUser == null) {
            return handleNoAuth(response);
        }
        if ( !StringUtils.equals(invokeUser.getSecretKey(),secretKey)){
            return handleNoAuth(response);
        }
//        //TODO  从redis判断是否存在nonce，执行后删除
//        if (!innerNonceService.removeNonce(nonce)) {
//            return handleNoAuth(response);
//        }
//        String secretKey = invokeUser.getSecretKey();
//        HashMap<String, String> headerMap = new HashMap<>();
//        headerMap.put("accessKey", accessKey);
//        headerMap.put("nonce", nonce);
//        headerMap.put("timestamp", timestamp);
//        String newSign = SignUtils.getSign(headerMap, secretKey, null);
//        if (sign == null || !newSign.equals(sign)) {
//            return handleNoAuth(response);
//        }

        //4.检测接口是否存在
        InterfaceInfo interfaceInfo = interfaceInfoService.getInterfaceInfo(requestURL, method);
        if (interfaceInfo==null){
//            return handleNoAuth(response);
            return handleReqErr(response,ResultUtils.error(ErrorCode.OPERATION_ERROR, "接口不存在"));
//            return handleReqErr(response,"\"{\"code\":50001,\"data\":null,\"message\":\"调用次数不足\"}\"");
        }
        Long userid = invokeUser.getId();
        Long id = interfaceInfo.getId();
        //todo 检测是否有足够请求次数
        if (!innerUserInterfaceInfoService.verifyCount(interfaceInfo.getId(), userid)) {
//            ObjectMapper mapper = new ObjectMapper();
//            String msg;
//            try {
//                msg = mapper.writeValueAsString(ResultUtils.error(ErrorCode.OPERATION_ERROR, "调用次数不足"));
//            } catch (JsonProcessingException e) {
//                throw new RuntimeException(e);
//            }
//            return handleReqErr(response, msg);
            return handleReqErr(response, ResultUtils.error(ErrorCode.OPERATION_ERROR, "调用次数不足"),HttpStatus.PAYMENT_REQUIRED);
        }
        //5.请求转发，调用模拟接口
        return handleResponse(exchange, chain,id,userid);


    }

    private Mono<Void> handleResponse(ServerWebExchange exchange, GatewayFilterChain chain,long interfaceInfoId,long userId) {
        try {
            String cookieName = "fromWhere";
            String cookieValue = "api-gateway";
            String cookieString = cookieName + "=" + cookieValue;

            // 修改请求，添加 Cookie
            exchange = exchange.mutate()
                    .request(request -> request.headers(headers -> headers.add("Cookie", cookieString)))
                    .build();
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
                                //7.调用成功接口次数++
                                boolean b = innerUserInterfaceInfoService.invokeCount(interfaceInfoId, userId);
                                log.info("接口次数＋1 "+(b?"成功":"失败"));
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
        log.info("无权访问");
        response.getHeaders().add("Content-Type", "application/json;charset=utf-8");
        DataBufferFactory bufferFactory = response.bufferFactory();
        ObjectMapper objectMapper = new ObjectMapper();
        DataBuffer wrap = null;
        try {
            wrap = bufferFactory.wrap(objectMapper.writeValueAsBytes(ResultUtils.error(ErrorCode.NO_AUTH_ERROR,"无权访问")));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        DataBuffer finalWrap = wrap;
        return response.writeWith(Mono.fromSupplier(() -> finalWrap));
    }
    public Mono<Void> handleReqErr(ServerHttpResponse response,Object msg,HttpStatus status) {
        response.setStatusCode(status);
//        response.headers().set("Content-Type", "application/json;charset=utf-8");
        response.getHeaders().add("Content-Type", "application/json;charset=utf-8");
        DataBufferFactory bufferFactory = response.bufferFactory();
        ObjectMapper objectMapper = new ObjectMapper();
        DataBuffer wrap = null;
        try {
            wrap = bufferFactory.wrap(objectMapper.writeValueAsBytes(msg));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        DataBuffer finalWrap = wrap;
        return response.writeWith(Mono.fromSupplier(() -> finalWrap));
    }
    public Mono<Void> handleReqErr(ServerHttpResponse response,Object msg) {
        return handleReqErr(response,msg,HttpStatus.LOCKED);
    }

    @Override
    public int getOrder() {
        return -1;
    }
}