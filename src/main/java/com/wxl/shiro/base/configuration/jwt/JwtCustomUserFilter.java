package com.wxl.shiro.base.configuration.jwt;

import com.alibaba.fastjson.JSONObject;
import com.auth0.jwt.interfaces.Claim;
import com.wxl.shiro.base.api.dto.Result;
import com.wxl.shiro.base.utils.RsaUtils;
import com.wxl.shiro.base.utils.ShiroPathCheckConstant;
import com.wxl.shiro.base.utils.jwt.JwtUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.authc.UserFilter;
import org.springframework.data.redis.core.RedisTemplate;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author Weixl
 * @date 2021/10/19
 *
 */
@Slf4j
public class JwtCustomUserFilter extends UserFilter {

    private RedisTemplate redisTemplate;

    public JwtCustomUserFilter(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @SneakyThrows
    @Override
    protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) {
        log.info("Jwt校验用户登录....");
        try {
            String token = ((HttpServletRequest) request).getHeader("token");
            // 判断传递Token是否进入了Redis的黑名单
            Object queryTokenRedis = redisTemplate.opsForValue().get(token + "-logout");
            if (Objects.nonNull(queryTokenRedis)) {
                return false;
            }
            // 校验Token是否有效 , 解析出Token中存储的用户信息内容
            Map<String, Claim> claims = JwtUtils.getClaims(token);
            if (Objects.isNull(claims) || claims.size() < 1) {
                return false;
            }
            String username = claims.get("username").asString();
            String password = claims.get("password").asString();
            String roleLabelString = claims.get("role").asString();
            // 使用 username和password ， 使用公钥将密码进行解密 成 MD5哈希数据库中存储的密码
            String decryptPassword = RsaUtils.decryptByPublicKey(password);
            // 使用password进行
            boolean flag = JwtUtils.verifyToken(token, username, password, decryptPassword, roleLabelString);
            if (flag) {
                // 判断后台是否对角色的资料进行编辑 , 有没有影响到当前用户
                List<String> roleLabelList = JSONObject.parseArray(roleLabelString, String.class);
                // 没编辑一个角色时，就存一个key，前缀一样，都获取处理进行判断处理
                Set<String> keyList = redisTemplate.keys(ShiroPathCheckConstant.JWT_ROLE_FILTER + "*");
                for (String key : keyList) {
                    if (roleLabelList.contains(key.split(ShiroPathCheckConstant.JWT_ROLE_FILTER)[1])) {
                        // 登录黑名单让重新登录
                        redisTemplate.opsForValue().set(token + "-logout", "1" , 3600 , TimeUnit.SECONDS);
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
    *  没有登录过滤器响应报错
    */
    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
        log.error("user not login check....");
        Subject subject = this.getSubject(request, response);
        // 返回错误码 , 这里可以做国际化处理
        HttpServletResponse httpServletResponse = (HttpServletResponse)response;
        Result<Void> result = Result.error("500", "没有登录");
        httpServletResponse.setHeader("Content-Type" , "application/json;charset=utf-8");
        httpServletResponse.getWriter().write(JSONObject.toJSONString(result));
        return false;
    }
}
