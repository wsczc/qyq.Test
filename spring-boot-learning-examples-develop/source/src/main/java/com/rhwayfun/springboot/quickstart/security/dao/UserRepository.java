package com.rhwayfun.springboot.quickstart.security.dao;

import com.rhwayfun.springboot.quickstart.security.datasource.model.User;
import com.rhwayfun.springboot.quickstart.security.datasource.model.UserAuthority;

import java.util.List;

/**
 * Created by chubin on 2017/8/13.
 */
public interface UserRepository{

    User findByUserName(String username);

    List<UserAuthority> findRoles(int userId);

}
