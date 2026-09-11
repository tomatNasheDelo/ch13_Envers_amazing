package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.hibernate.envers.query.criteria.MatchMode;
import org.hibernate.ReplicationMode;
import org.hibernate.Session;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.query.AuditQuery;


import org.junit.jupiter.api.Test;

import com.example.envers.Item;
import com.example.envers.User;

public class Envers {


    private EntityManagerFactory emf = Persistence.createEntityManagerFactory("ch13");


    @Test 
    public void auditLogging(){  // <--  подробно этот тестовый метод это пример из классической manning книги 
                                  // Java Persistence with Spring Data and Hibernate 

         Long ITEM_ID;
         Long USER_ID;

         {
            EntityManager em = emf.createEntityManager();
            em.getTransaction().begin();

            User user = new User("johndoe");
            em.persist(user);

            Item item = new Item("Foo", user);
            em.persist(item);

            em.getTransaction().commit();
            em.close();

            ITEM_ID = item.getId();
            USER_ID = user.getId();

         }

         Date TIMESTAMP_CREATE = new Date();

         {
             EntityManager em = emf.createEntityManager();
             em.getTransaction().begin();

             Item item = em.find(Item.class, ITEM_ID);
             item.setName("Bar");
             item.getSeller().setUsername("doejohn");

             em.getTransaction().commit();
             em.close();
         }

         Date TIMESTAMP_UPDATE = new Date();
         {

            EntityManager em = emf.createEntityManager();
            em.getTransaction().begin();

            Item item = em.find(Item.class, ITEM_ID);
            em.remove(item);

            em.getTransaction().commit();
            em.close();
         }

         Date TIMESTAMP_DELETE = new Date();

         {

            EntityManager em = emf.createEntityManager();
            em.getTransaction().begin();

            AuditReader auditReader = AuditReaderFactory.get(em);

            Number revisionCreate = auditReader.getRevisionNumberForDate(TIMESTAMP_CREATE);
            Number revisionUpdate = auditReader.getRevisionNumberForDate(TIMESTAMP_UPDATE);
            Number revisionDelete = auditReader.getRevisionNumberForDate(TIMESTAMP_DELETE);


            List<Number> itemRevisions = auditReader.getRevisions(Item.class, ITEM_ID);
            assertEquals(3, itemRevisions.size());
            for (Number itemRevision : itemRevisions){

                Date itemRevisionTimestamp = auditReader.getRevisionDate(itemRevision);
                
            }

            List<Number> userRevisions = auditReader.getRevisions(User.class, USER_ID);
            assertEquals(2, userRevisions.size());

            em.clear();
            {

                AuditQuery query = auditReader.createQuery() 
                           .forRevisionsOfEntity(Item.class, false, false);

                           @SuppressWarnings ("unchecked")
                           List<Object[]> result = query.getResultList();
                           for (Object[] tuple: result){
                                  
                               Item item = (Item) tuple[0];
                               DefaultRevisionEntity revision  = (DefaultRevisionEntity) tuple[1];
                               RevisionType revisionType = (RevisionType) tuple[2];



                               if(revision.getId() == 1){
                                assertEquals(RevisionType.ADD, revisionType);
                                assertEquals("Foo", item.getName());
                               } else if (revision.getId() == 2){
                                assertEquals(RevisionType.MOD, revisionType);
                                assertEquals("Bar", item.getName());
                               } else if (revision.getId() == 3){
                                assertEquals(RevisionType.DEL, revisionType);
                                assertNull(item);
                               }

                           }
            }


                em.clear();
                {


                    Item item = auditReader.find(Item.class, ITEM_ID, revisionCreate);
                    assertEquals("Foo", item.getName());
                    assertEquals("johndoe", item.getSeller().getUsername());

                    Item modifiedItem = auditReader.find(Item.class, ITEM_ID, revisionUpdate);
                    assertEquals("Bar", modifiedItem.getName());
                    assertEquals("doejohn", modifiedItem.getSeller().getUsername());

                    Item deletedItem = auditReader.find(Item.class, ITEM_ID, revisionDelete);
                    assertNull(deletedItem);

                    User user = auditReader.find(User.class, USER_ID, revisionDelete);
                    assertEquals("doejohn", user.getUsername());
                }
                em.clear();
                {

                    AuditQuery query = auditReader.createQuery() 
                               .forEntitiesAtRevision(Item.class, revisionUpdate);

                    query.add(AuditEntity.property("name").like("Ba", MatchMode.START));

                    query.add(AuditEntity.relatedId("seller").eq(USER_ID));


                    query.addOrder(AuditEntity.property("name").desc());

                    query.setFirstResult(0);
                    query.setMaxResults(10);

                    assertEquals(1, query.getResultList().size());
                    Item result = (Item)query.getResultList().get(0);
                    assertEquals("doejohn", result.getSeller().getUsername());
                }

                em.clear();

                {

                    AuditQuery query = auditReader.createQuery() 
                                     .forEntitiesAtRevision(Item.class, revisionUpdate);

                                     query.addProjection(AuditEntity.property("name"));

                                     assertEquals(1, query.getResultList().size());
                                     String result = (String)query.getSingleResult();
                                     assertEquals("Bar", result);


                }
                   em.clear();
                   {

                    User user = auditReader.find(User.class, USER_ID, revisionCreate);

                    em.unwrap(Session.class) 
                             .replicate(user, ReplicationMode.OVERWRITE);

                    em.flush();
                    em.clear();

                    user = em.find(User.class, USER_ID);
                    assertEquals("johndoe", user.getUsername());
                   }

                   em.getTransaction().commit();
                   em.close();
            





         }


    }
    
}
