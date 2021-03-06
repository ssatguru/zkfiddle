package org.zkoss.fiddle.dao;

import java.sql.SQLException;
import java.util.List;

import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.transform.BasicTransformerAdapter;
import org.springframework.orm.hibernate3.HibernateCallback;
import org.zkoss.fiddle.core.utils.CacheHandler;
import org.zkoss.fiddle.core.utils.FiddleCache;
import org.zkoss.fiddle.dao.api.ICaseTagDao;
import org.zkoss.fiddle.model.Case;
import org.zkoss.fiddle.model.CaseRecord;
import org.zkoss.fiddle.model.CaseTag;
import org.zkoss.fiddle.model.Tag;
import org.zkoss.fiddle.model.api.ICase;
import org.zkoss.fiddle.visualmodel.TagCaseListVO;

@SuppressWarnings("unchecked")
public class CaseTagDaoImpl extends AbstractDao implements ICaseTagDao{

	public List<CaseTag> list() {
		return getHibernateTemplate().find("from CaseTag");
	}

	public void save(ICase c, Tag t) {
		CaseTag ct = new CaseTag();
		ct.setCaseId(c.getId());
		ct.setTagId(t.getId());
		super.saveOrUdateObject(ct);
		FiddleCache.CaseTag.removeAll();
	}

	public void replaceTags(final ICase _case,final List<Tag> list) {
		getTxTemplate().execute(new HibernateTransacationCallback<Void>(getHibernateTemplate()) {
			public Void doInHibernate(Session session) throws HibernateException, SQLException {

				//decrease the amount
				Query query = session.createQuery("update Tag set amount = amount -1 "+
						" where id in (select tagId from CaseTag where caseId = :caseId)");
				query.setLong("caseId",_case.getId());
				query.executeUpdate();

				//removeing the tag
				Query query2 = session.createQuery("delete from CaseTag where caseId = :caseId");
				query2.setLong("caseId",_case.getId());
				query2.executeUpdate();

				for(Tag tag:list){
					CaseTag caseTag = new CaseTag();
					caseTag.setCaseId(_case.getId());
					caseTag.setTagId(tag.getId());
					session.save(caseTag);
				}

				//add the new amount
				Query query3 = session.createQuery("update Tag set amount = amount +1  "+
				" where id in (select tagId from CaseTag where caseId = :caseId)");
				query3.setLong("caseId",_case.getId());
				query3.executeUpdate();

				FiddleCache.CaseTag.removeAll();
				return null;
			}
		});

	}



	public List<Tag> findTagsBy(final ICase c) {
		return getHibernateTemplate().execute(new HibernateCallback<List<Tag>>() {

			public List<Tag> doInHibernate(Session session) throws HibernateException, SQLException {
				Query qu = session.createQuery("select t from Tag t,CaseTag tc "
						+ " where t.id = tc.tagId and tc.caseId = :caseId ");
				qu.setLong("caseId", c.getId());
				return qu.list();
			}
		});
	}

	public List<Tag> findTagsBy(final ICase c, final int pageIndex, final int pageSize) {
		return getHibernateTemplate().execute(new HibernateCallback<List<Tag>>() {

			public List<Tag> doInHibernate(Session session) throws HibernateException, SQLException {
				Query qu = session.createQuery("select t from Tag t,CaseTag tc "
						+ " where t.id = tc.tagId and tc.caseId = :caseId ");
				qu.setLong("caseId", c.getId());

				setPage(qu,pageIndex,pageSize);
				return qu.list();
			}
		});
	}

	public void saveOrUdate(CaseTag m) {
		FiddleCache.CaseTag.removeAll();
		super.saveOrUdateObject(m);
	}

	public CaseTag get(Long id) {
		throw new UnsupportedOperationException("unsupported");
	}

	public void remove(CaseTag m) {
		getHibernateTemplate().delete(m);
	}

	public void remove(final Long id) {
		throw new UnsupportedOperationException("unsupported");
	}

	private static String HQL_findCaseByTag = "select cas from CaseRecord c,Case cas, CaseTag tc "
			+ " where  tc.tagId = :tagId and c.caseId = tc.caseId and c.type = :type and cas.id = tc.caseId "+
			" order by c.amount desc";

	/**
	 * This one is also a time consuming one ,
	 * need to find a better approach , I do believe I miss something here; :-(
	 */
	public List<TagCaseListVO> findCaseListsBy(final Tag tag,final int pageIndex,final int pageSize,final boolean loadTag) {
		return (List<TagCaseListVO>) FiddleCache.CaseTag.execute(new CacheHandler<List<TagCaseListVO>>() {
			protected List<TagCaseListVO> execute() {
				return getHibernateTemplate().execute(new HibernateCallback<List<TagCaseListVO>>() {

					public List<TagCaseListVO> doInHibernate(final Session session) throws HibernateException, SQLException {
						Query query = session.createQuery(HQL_findCaseByTag);
						query.setLong("tagId", tag.getId());
						query.setLong("type", CaseRecord.Type.View.value());
						query.setResultTransformer(new BasicTransformerAdapter() {
							private static final long serialVersionUID = 4466976604759893212L;

							public Object transformTuple(Object[] tuple, String[] aliases) {
								TagCaseListVO tcvo = new TagCaseListVO();

								Case cas = (Case) tuple[0];
								tcvo.setCase(cas);
								if(loadTag){
									Query query = session.createQuery("select t from Tag t,CaseTag ct "
											+ " where t.id = ct.tagId and ct.caseId = :caseId ");
									query.setLong("caseId", cas.getId());
									List<Tag> list = query.list();
	
									tcvo.setTags(list);
								}
								return tcvo;
							}
						});
						setPage(query,pageIndex,pageSize);
						return query.list();
					}
				});
			}
			protected String getKey() {
				return tag.getId()+":"+pageIndex+":"+pageSize;
			}
		});

	}

	private static String HQL_countCaseByTag = "select count(c) from CaseRecord c, CaseTag tc "
			+ " where c.caseId = tc.caseId and c.type = 0 and tc.tagId = :tagId ";

	public Long countCaseRecordsBy(final Tag tag) {
		return getHibernateTemplate().execute(new HibernateCallback<Long>() {

			public Long doInHibernate(Session session) throws HibernateException, SQLException {
				Query query = session.createQuery(HQL_countCaseByTag);
				query.setLong("tagId", tag.getId());
				return (Long) query.uniqueResult();
			}
		});
	}

	public List<Case> findCasesBy(final Tag tag,final int pageIndex,final int pageSize) {
		return getHibernateTemplate().execute(new HibernateCallback<List<Case>>() {

			public List<Case> doInHibernate(Session session) throws HibernateException, SQLException {
				Query qu = session.createQuery("select c from Case c,CaseTag tc "
						+ " where c.id = tc.caseId and tc.tagId = :tagId ");
				qu.setLong("tagId", tag.getId());

				setPage(qu,pageIndex,pageSize);
				return qu.list();
			}
		});
	}

	public List<Case> findCasesBy(final String tagName,final int pageIndex,final int pageSize) {
		return getHibernateTemplate().execute(new HibernateCallback<List<Case>>() {

			public List<Case> doInHibernate(Session session) throws HibernateException, SQLException {
				Query qu = session.createQuery("select c from Case c,CaseTag tc,Tag t "
						+ " where t.name= :tagName and tc.tagId = t.id and c.id = tc.caseId order by tc.caseId desc");
				qu.setString("tagName", tagName);

				setPage(qu,pageIndex,pageSize);
				return qu.list();
			}
		});
	}
}
