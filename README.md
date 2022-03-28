#本项目为Shiro测试使用
------
- 2022-03-22
- dev-v1.0.1.0
    ```
    添加JWT功能与Shiro整合实现 ·单点登录·
    处理方案逻辑：
    1. 用户登出时，通过Redis记录JwtToken到黑名单，用户登录Filter过滤进行判断
    2. 用户Role角色信息记录在JwtToken中，每次通过token中获取到进行判断，
    3. 如对角色信息有过修改那么Redis中记录标识，在角色鉴权FIlter过滤时进行判断是否走数据库角色查询判断.
    4. 推翻上面第三条，当操作角色相关的变动时，就记录一个标识到Redis中，在UserFilter进行过滤时判断拥有标记角色的就直接让重新登录.