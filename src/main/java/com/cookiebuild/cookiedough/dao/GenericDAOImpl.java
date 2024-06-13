package com.cookiebuild.cookiedough.dao;


import com.cookiebuild.cookiedough.CookieDough;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.UUID;

public class GenericDAOImpl<T> implements GenericDAO<T> {
    private final Class<T> entityClass;

    public GenericDAOImpl(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    @Override
    public void save(T entity) {
        Transaction transaction = null;
        try (Session session = CookieDough.sessionFactory.openSession()) {
            transaction = session.beginTransaction();
            session.save(entity);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            e.printStackTrace();
        }
    }

    @Override
    public T findById(UUID id) {
        try (Session session = CookieDough.sessionFactory.openSession()) {
            return session.get(entityClass, id);
        }
    }

    @Override
    public void delete(T entity) {
        Transaction transaction = null;
        try (Session session = CookieDough.sessionFactory.openSession()) {
            transaction = session.beginTransaction();
            session.delete(entity);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            e.printStackTrace();
        }
    }

    @Override
    public void update(T entity) {
        Transaction transaction = null;
        try (Session session = CookieDough.sessionFactory.openSession()) {
            transaction = session.beginTransaction();
            session.merge(entity);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            e.printStackTrace();
        }
    }
}