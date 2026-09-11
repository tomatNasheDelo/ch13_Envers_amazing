# Подробный разбор теста `auditLogging()` — Hibernate Envers

## Что такое Envers и зачем он нужен

**Hibernate Envers** — это модуль Hibernate для **автоматического версионирования сущностей**: 
он отслеживает **каждое** изменение (`INSERT`, `UPDATE`, `DELETE`) 
для помеченных сущностей и сохраняет **полный снимок их состояния на каждый момент времени** в отдельных, 
автоматически генерируемых "теневых" таблицах (обычно с суффиксом `_AUD`, например `ITEM_AUD`, `USERS_AUD`).

Это принципиально **другой уровень** по сравнению с `AuditLogInterceptor`, который вы разбирали раньше:
smotri table in resource

Сущности `Item` и `User` в этом примере, очевидно, аннотированы `@Audited` 
(в самом коде теста это не показано, но подразумевается — иначе Envers их не отслеживал бы).
---

## Часть 1: создание данных (Revision 1)
```java
User user = new User("johndoe");
em.persist(user);

Item item = new Item("Foo", user);
em.persist(item);

em.getTransaction().commit();
em.close();

ITEM_ID = item.getId();
USER_ID = user.getId();
```
Создаются `User` и `Item`. При коммите транзакции Envers **автоматически** создаёт **ревизию №1** 
(первую запись истории) — сохраняет снимок обеих сущностей в момент их создания в таблицах `ITEM_AUD` и `USERS_AUD`, 
с типом изменения `ADD` (добавление).

```java
Date TIMESTAMP_CREATE = new Date();
```
Запоминается временная метка **сразу после** этой транзакции — понадобится позже, 
чтобы найти соответствующую ревизию по времени.
---
## Часть 2: изменение данных (Revision 2)

```java
Item item = em.find(Item.class, ITEM_ID);
item.setName("Bar");
item.getSeller().setUsername("doejohn");

em.getTransaction().commit();
```
Меняется имя товара (`"Foo"` → `"Bar"`) **и** имя пользователя-продавца (`"johndoe"` → `"doejohn"`). 
При коммите Envers создаёт **ревизию №2** — важно: **обе** сущности (`Item` и `User`) 
изменились в рамках **одной и той же** транзакции, значит, они получат **один и тот же номер ревизии**.
Тип изменения для обеих — `MOD` (модификация).

```java
Date TIMESTAMP_UPDATE = new Date();
```
Метка времени после этой транзакции.

---

## Часть 3: удаление (Revision 3)

```java
Item item = em.find(Item.class, ITEM_ID);
em.remove(item);

em.getTransaction().commit();
```
`Item` удаляется. Envers создаёт **ревизию №3** с типом `DEL` (удаление) — сохраняется факт, 
что на этой ревизии сущность перестала существовать (сам объект в этой ревизии будет представлен как `null` 
или как специальная "пустая" запись, в зависимости от настройки).

```java
Date TIMESTAMP_DELETE = new Date();
```

---

## Часть 4: чтение истории через `AuditReader`

```java
AuditReader auditReader = AuditReaderFactory.get(em);
```
`AuditReader` — это главный API Envers для **чтения** истории изменений (аналог `EntityManager`, 
но только для истории, а не для текущего состояния).

### 4.1 — Получение номера ревизии по временной метке

```java
Number revisionCreate = auditReader.getRevisionNumberForDate(TIMESTAMP_CREATE);
Number revisionUpdate = auditReader.getRevisionNumberForDate(TIMESTAMP_UPDATE);
Number revisionDelete = auditReader.getRevisionNumberForDate(TIMESTAMP_DELETE);
```
Envers присваивает каждой ревизии **не только номер, но и временную метку**. 
Этот метод позволяет найти "какая ревизия действовала на момент времени X" — полезно, когда вы знаете дату, но не знаете точный номер ревизии.

### 4.2 — Получение списка всех ревизий для конкретной сущности

```java
List<Number> itemRevisions = auditReader.getRevisions(Item.class, ITEM_ID);
assertEquals(3, itemRevisions.size());
```
`Item` с данным ID был затронут в **трёх** ревизиях (создание, изменение, удаление) — проверка подтверждает, 
что Envers действительно зафиксировал все три события.

```java
for (Number itemRevision : itemRevisions){
    Date itemRevisionTimestamp = auditReader.getRevisionDate(itemRevision);
}
```
Для каждой ревизии можно узнать её временную метку — цикл здесь просто демонстрирует такую возможность (результат нигде не проверяется).

```java
List<Number> userRevisions = auditReader.getRevisions(User.class, USER_ID);
assertEquals(2, userRevisions.size());
```
`User` был затронут только в **двух** ревизиях — создание (Revision 1) и изменение username (Revision 2). 
Пользователь никогда не удалялся, поэтому у него на одну ревизию меньше, чем у `Item`.

---

## Часть 5: `AuditQuery` — запрос всей истории ревизий сущности

```java
AuditQuery query = auditReader.createQuery()
        .forRevisionsOfEntity(Item.class, false, false);

List<Object[]> result = query.getResultList();
for (Object[] tuple: result){
    Item item = (Item) tuple[0];
    DefaultRevisionEntity revision = (DefaultRevisionEntity) tuple[1];
    RevisionType revisionType = (RevisionType) tuple[2];
    ...
}
```

`forRevisionsOfEntity(Item.class, false, false)` строит запрос, возвращающий **все ревизии всех экземпляров** 
`Item`. Два `false`-параметра означают:
- первый `false` — **не** возвращать саму сущность как единственный результат (вместо этого — кортеж `[entity,
- revisionInfo, revisionType]`);
- второй `false` — **не** включать только "удалённые" ревизии (`DEL`) с `null`-состоянием без полей — то есть,
- несмотря на это, `DEL`-ревизия всё равно попадёт в результат, но с `item == null`.

Каждая строка результата — это `Object[]` из трёх элементов:
1. **сама сущность** в этой конкретной ревизии (или `null`, если ревизия — удаление);
2. **метаданные ревизии** (`DefaultRevisionEntity` — содержит номер ревизии, timestamp);
3. **тип изменения** (`RevisionType.ADD`/`MOD`/`DEL`).

```java
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
```
Проверяется полная история: на ревизии 1 — добавление с именем `"Foo"`;
на ревизии 2 — изменение на `"Bar"`; на ревизии 3 — удаление, сущность равна `null`.

---

## Часть 6: `auditReader.find()` — получение сущности на конкретный момент времени (snapshot)

```java
Item item = auditReader.find(Item.class, ITEM_ID, revisionCreate);
assertEquals("Foo", item.getName());
assertEquals("johndoe", item.getSeller().getUsername());
```
Это, пожалуй, **самая мощная возможность** Envers — можно получить сущность 
**такой, какой она была на конкретной ревизии**, 
включая её связанные сущности (`seller`). На ревизии создания `Item.name == "Foo"`, а связанный `seller.username == "johndoe"` 
— то есть до того, как пользователь сменил имя.

```java
Item modifiedItem = auditReader.find(Item.class, ITEM_ID, revisionUpdate);
assertEquals("Bar", modifiedItem.getName());
assertEquals("doejohn", modifiedItem.getSeller().getUsername());
```
На ревизии обновления — уже `"Bar"` и `"doejohn"`. Обратите внимание: это **два разных объекта** `
item` и `modifiedItem` — исторические снимки, каждый "заморожен" в своём моменте времени, хотя оба ссылаются 
на ту же реальную запись с ID = `ITEM_ID`.

```java
Item deletedItem = auditReader.find(Item.class, ITEM_ID, revisionDelete);
assertNull(deletedItem);
```
На ревизии удаления сущность уже недоступна — `find()` возвращает `null`.

```java
User user = auditReader.find(User.class, USER_ID, revisionDelete);
assertEquals("doejohn", user.getUsername());
```
А вот `User` (в отличие от `Item`) **никогда не удалялся**, поэтому даже "на момент ревизии удаления Item" 
пользователь всё ещё существует — и его состояние соответствует **последней** 
известной ревизии до этого момента (Revision 2, где имя уже `"doejohn"`), потому что Envers для 
несуществующей ровно на этой ревизии сущности возвращает её состояние по **последней предшествующей** ревизии.

---

## Часть 7: `AuditQuery` с условиями, сортировкой и пагинацией

```java
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
```

`forEntitiesAtRevision(Item.class, revisionUpdate)` — запрос **всех** сущностей `Item`, 
какими они были **именно на этой конкретной ревизии** (в отличие от `forRevisionsOfEntity`, 
который берёт всю историю одной конкретной сущности по ID). Это аналог обычного JPQL/Criteria-запроса, 
но выполняемого **против исторических данных**, а не текущего состояния:

- `AuditEntity.property("name").like("Ba", MatchMode.START)` — фильтр: имя начинается с `"Ba"` (эквивалент `LIKE 'Ba%'`);
- `AuditEntity.relatedId("seller").eq(USER_ID)` — фильтр по связанной сущности (по ID продавца);
- `.addOrder(...)` — сортировка по имени, по убыванию;
- `.setFirstResult(0).setMaxResults(10)` — пагинация (первая страница, до 10 результатов).

То есть Envers предоставляет **полноценный query-язык** (аналог Criteria API), но 
применяемый к историческим данным — можно искать не только "текущие" записи, но и 
"как выглядели все товары на конкретную дату, у которых имя начиналось на 'Ba'".

---

## Часть 8: проекция — выборка одного конкретного поля

```java
AuditQuery query = auditReader.createQuery()
        .forEntitiesAtRevision(Item.class, revisionUpdate);
query.addProjection(AuditEntity.property("name"));

assertEquals(1, query.getResultList().size());
String result = (String)query.getSingleResult();
assertEquals("Bar", result);
```
`addProjection(...)` заставляет запрос вернуть **не всю сущность**,
а только значение конкретного поля (`name`) — аналог `SELECT name FROM ...` вместо `SELECT * FROM ...`. 
Результат — просто строка `"Bar"`.

---

## Часть 9: `replicate()` — восстановление старого состояния через уже знакомый вам механизм

```java
User user = auditReader.find(User.class, USER_ID, revisionCreate);

em.unwrap(Session.class)
         .replicate(user, ReplicationMode.OVERWRITE);

em.flush();
em.clear();

user = em.find(User.class, USER_ID);
assertEquals("johndoe", user.getUsername());
```

Это красивое завершение теста, объединяющее две ранее изученные вами темы: сначала через Envers 
**достаётся исторический снимок** пользователя на момент **создания** (`revisionCreate`), 
где `username == "johndoe"` (то есть **до** переименования в `"doejohn"`). Затем этот исторический объект 
**реплицируется обратно** в основную таблицу `USERS` через уже знакомый вам `Session.replicate(..., ReplicationMode.OVERWRITE)` — 
то есть текущая "боевая" запись пользователя **перезаписывается** тем состоянием, которое было на момент создания.

После `flush()` + `clear()` + повторного `find()` — подтверждается, что текущее состояние в основной таблице 
действительно **откатилось** к `"johndoe"` — то есть Envers использовался не только для просмотра истории, 
но и как источник данных для **восстановления (rollback) предыдущего состояния** через стандартный Hibernate-механизм репликации.

---

## Итоговая картина — чему учит этот тест

Тест демонстрирует полный цикл возможностей Envers:
1. **Автоматическое версионирование** — каждое изменение отслеживаемой сущности создаёт новую ревизию без
2. единой строчки ручного кода аудита (в отличие от `AuditLogInterceptor`).
3. **Получение списка ревизий** конкретной сущности (`getRevisions`).
4. **Получение сущности "как она выглядела" на конкретный момент времени** (`find(Class, id, revision)`).
5. **Запросы к историческим данным** с фильтрами, сортировкой, пагинацией и проекциями (`AuditQuery`) — то есть
6.  полноценный "поиск в прошлом", а не только точечное чтение.
7. **Практическое применение истории** — восстановление предыдущего состояния объекта через `replicate()`,
8.  что показывает, как Envers может использоваться не просто "для галочки аудита", а как реальный механизм
9.  отката изменений (rollback/undo функциональность на уровне бизнес-данных).
