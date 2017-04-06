package com.dsi.checkauthorization.dao.impl;

import com.dsi.checkauthorization.util.SessionUtil;
import org.hibernate.Session;

/**
 * Created by sabbir on 6/24/16.
 */
public class BaseDao {

    public Session getSession() {
        Session session = SessionUtil.getSession();
        session.beginTransaction();
        return session;
    }

    void close(Session session) {
        session.getTransaction().commit();
        session.close();
    }
}
