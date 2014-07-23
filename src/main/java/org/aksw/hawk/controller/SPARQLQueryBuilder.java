package org.aksw.hawk.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.aksw.autosparql.commons.qald.Question;
import org.aksw.hawk.nlp.posTree.MutableTreeNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.impl.ResourceImpl;

public class SPARQLQueryBuilder {
	Logger log = LoggerFactory.getLogger(SPARQLQueryBuilder.class);

	public Map<String, Set<RDFNode>> build(Question q) {
		Map<String, Set<RDFNode>> answer = Maps.newHashMap();
		// build projection part
		Set<StringBuilder> queryStrings = buildProjectionPart(q);
		for (StringBuilder queryString : queryStrings) {
			// TODO if filter is too large split it
			answer.put(queryString.toString(), sparql(queryString.toString()));
		}
		return answer;
	}

	private Set<StringBuilder> buildProjectionPart(Question q) {
		Set<StringBuilder> queries = Sets.newHashSet();
		List<MutableTreeNode> bottomUp = Lists.newArrayList();
		// iterate through left tree part
		// assumption: this part of the tree is a path
		MutableTreeNode tmp = q.tree.getRoot().getChildren().get(0);
		while (tmp != null) {
			bottomUp.add(tmp);
			if (!tmp.getChildren().isEmpty()) {
				tmp = tmp.getChildren().get(0);
			} else {
				tmp = null;
			}
		}
		bottomUp = Lists.reverse(bottomUp);
		for (int i = 0; i < bottomUp.size(); ++i) {
			MutableTreeNode bottom = bottomUp.get(i);
			String bottomposTag = bottom.posTag;
			MutableTreeNode top = bottom.parent;
			String topPosTag = bottom.parent.posTag;
			// head of this node is root element
			if (top.parent == null) {
				if (bottomposTag.matches("WRB|WP|NN(.)*")) {
					// is either from Where or Who
					if (bottom.getAnnotations().size() > 0) {
						for (ResourceImpl annotation : bottom.getAnnotations()) {
							StringBuilder queryString = new StringBuilder("SELECT ?proj WHERE {\n");
							queryString.append("?proj a <" + annotation + ">.\n}");
							queries.add(queryString);
							// TODO add super class for things like City ->
							// Settlement
						}
					} else {
						log.error("Too many or too less annotations for projection part of the tree!", q.languageToQuestion.get("en"));
					}
				} else if (bottomposTag.equals("CombinedNN")) {
					// combined nouns are lists of abstracts containing does
					// words, i.e., type constraints
					if (bottom.getAnnotations().size() > 0) {
						StringBuilder queryString = new StringBuilder("SELECT ?proj WHERE {\n");
						queryString.append("?proj ?p ?o.\n").append("FILTER (?proj IN (\n");
						for (ResourceImpl annotation : bottom.getAnnotations()) {
							queryString.append("<" + annotation.getURI() + "> , ");
						}
						queryString.deleteCharAt(queryString.lastIndexOf(",")).append(")).}");
						queries.add(queryString);
					} else {
						log.error("Too less annotations for projection part of the tree!", q.languageToQuestion.get("en"));
					}
				} else {
					// strange case
					// since entities should not be the question word type
					log.error("Strange case that never should happen: " + bottomposTag);
				}
			} else {
				// TODO build it in a way, that says that down here are only
				// projection variable constraining modules that need to be
				// advanced by the top
				// heuristically say that here NNs or VBs stand for a predicates
				if (bottomposTag.equals("CombinedNN") && topPosTag.matches("VB(.)*|NN(.)*")) {
					for (ResourceImpl predicates : top.getAnnotations()) {
						StringBuilder queryString = new StringBuilder("SELECT ?proj WHERE {\n");
						queryString.append("?proj <" + predicates + "> ?o.\n").append("FILTER (?proj IN (\n");
						for (ResourceImpl annotation : bottom.getAnnotations()) {
							queryString.append("<" + annotation.getURI() + "> , ");
						}
						queryString.deleteCharAt(queryString.lastIndexOf(",")).append(")).}");
						queries.add(queryString);
						queryString = new StringBuilder("SELECT ?proj WHERE {\n");
						queryString.append("?o <" + predicates + "> ?proj.\n").append("FILTER (?proj IN (\n");
						for (ResourceImpl annotation : bottom.getAnnotations()) {
							queryString.append("<" + annotation.getURI() + "> , ");
						}
						queryString.deleteCharAt(queryString.lastIndexOf(",")).append(")).}");
						queries.add(queryString);
					}
					i++;
				} else if (bottomposTag.equals("ADD") && topPosTag.matches("VB(.)*|NN(.)*")) {
					// either way it is an unprecise verb binding
					for (ResourceImpl annotation : top.getAnnotations()) {
						StringBuilder queryString = new StringBuilder("SELECT ?proj WHERE {\n");
						queryString.append("?proj <" + annotation + "> <" + bottom.label + ">.\n}");
						queries.add(queryString);
					}
					// or it stems from a full-text look up (+ reversing of the
					// predicates)
					StringBuilder queryString = new StringBuilder("SELECT ?proj WHERE {\n");
					queryString.append("?proj ?p <" + bottom.label + ">.\n").append("FILTER (?proj IN (\n");
					for (ResourceImpl annotation : top.getAnnotations()) {
						queryString.append("<" + annotation.getURI() + "> , ");
					}
					queryString.deleteCharAt(queryString.lastIndexOf(",")).append(")).}");
					queries.add(queryString);
					queryString = new StringBuilder("SELECT ?proj WHERE {\n");
					queryString.append("<" + bottom.label + "> ?p ?proj.\n").append("FILTER (?proj IN (\n");
					for (ResourceImpl annotation : top.getAnnotations()) {
						queryString.append("<" + annotation.getURI() + "> , ");
					}
					queryString.deleteCharAt(queryString.lastIndexOf(",")).append(")).}");
					queries.add(queryString);
					i++;
				} else {
					log.error("Strange case that never should happen: " + bottomposTag);
				}
			}
		}
		return queries;
	}

	private Set<RDFNode> sparql(String query) {
		ArrayList<String> queries = Lists.newArrayList();
		Set<RDFNode> set = Sets.newHashSet();
		QueryExecution qexec = null;
		try {
			if (query.contains("FILTER")) {
				queries = splitLongFilterSPARQL(query);
			} else {
				queries.add(query);
			}
			for (String q : queries) {
				qexec = QueryExecutionFactory.sparqlService("http://dbpedia.org/sparql", q);
				ResultSet results = qexec.execSelect();
				while (results.hasNext()) {
					set.add(results.next().get("?proj"));
				}
			}
		} catch (Exception e) {
			log.error("Query: " + queries.get(0), e);
		} finally {
			if (qexec != null) {
				qexec.close();
			}
		}
		return set;
	}

	private ArrayList<String> splitLongFilterSPARQL(String query) {
		ArrayList<String> queries = Lists.newArrayList();

		query = query.replaceAll("\n", "");
		Pattern pattern = Pattern.compile(".+FILTER\\s*\\(\\s*\\?proj IN\\s*\\((.+)\\)\\).+");
		Matcher m = pattern.matcher(query);
		log.debug("FILTER Pattern found" + m.find());
		String group = m.group(1);
		query = query.replace(group, "XXAKSWXX");
		ArrayList<String> uris = Lists.newArrayList();
		for (String uri : group.split(", ")) {
			uris.add(uri.trim());
		}
		int sizeOfFilterThreshold = 50;
		for (int i = 0; i < uris.size();) {
			String filter = "";
			for (int sizeOfFilter = 0; sizeOfFilter < sizeOfFilterThreshold && sizeOfFilter + i < uris.size(); sizeOfFilter++) {
				filter += uris.get(i + sizeOfFilter);
				if (sizeOfFilter < (sizeOfFilterThreshold - 1) && sizeOfFilter + i < (uris.size() - 1)) {
					filter += ",";
				}
			}
			i += sizeOfFilterThreshold;
			String newQuery = query.replace("XXAKSWXX", filter);
			queries.add(newQuery);
		}
		return queries;
	}

	public static void main(String args[]) {
		String query = "SELECT ?proj WHERE {?proj ?p ?o. FILTER " + "(?proj IN (<http://(1)> , <http://2,3> , <http://3> , <http://4> , <http://5>, <http://6> , <http://7>   ))}";
		SPARQLQueryBuilder sqb = new SPARQLQueryBuilder();
		ArrayList<String> i = sqb.splitLongFilterSPARQL(query);
		for (String q : i) {
			System.out.println(q);
		}
	}
}
