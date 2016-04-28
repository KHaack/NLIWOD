package org.aksw.mlqa.analyzer;

import java.util.ArrayList;

import org.aksw.mlqa.analyzer.comperative.Comperative;
import org.aksw.mlqa.analyzer.entityType.EntityDate;
import org.aksw.mlqa.analyzer.entityType.EntityLocation;
import org.aksw.mlqa.analyzer.entityType.EntityMoney;
import org.aksw.mlqa.analyzer.entityType.EntityOrganization;
import org.aksw.mlqa.analyzer.entityType.EntityPercent;
import org.aksw.mlqa.analyzer.entityType.EntityPerson;
import org.aksw.mlqa.analyzer.numberOfToken.NumberOfToken;
import org.aksw.mlqa.analyzer.querytype.QueryResourceTypeAnalyzer;
import org.aksw.mlqa.analyzer.questiontype.QuestionTypeAnalyzer;
import org.aksw.mlqa.analyzer.questionword.QuestionWord;
import org.aksw.mlqa.analyzer.superlative.Superlative;

import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;

public class Analyzer {

	private ArrayList<IAnalyzer> analyzers;
	private Attribute fmeasureAtt = new Attribute("fmeasure");
	public FastVector fvWekaAttributes = new FastVector();

	/**
	 *
	 * @param ClassAttribute
	 *            classes to be differentiated FastVector fvClassVal = new
	 *            FastVector(2); fvClassVal.addElement("positive");
	 *            fvClassVal.addElement("negative");
	 */
	
	public Analyzer() {

		analyzers = new ArrayList<IAnalyzer>();
		// !!! ADD ANALYZERS HERE !!!
		analyzers.add(new QuestionTypeAnalyzer());
		analyzers.add(new QueryResourceTypeAnalyzer());
		analyzers.add(new QuestionWord());
		analyzers.add(new NumberOfToken());
		analyzers.add(new Superlative());
		analyzers.add(new Comperative());
		analyzers.add(new EntityPerson());
		analyzers.add(new EntityMoney());
		analyzers.add(new EntityLocation());
		analyzers.add(new EntityPercent());
		analyzers.add(new EntityOrganization());
		analyzers.add(new EntityDate());

		
		
		// Declare the feature vector, register their attributes
		for (IAnalyzer analyzer : analyzers) {
			fvWekaAttributes.addElement(analyzer.getAttribute());
		}
	}

	/**
	 *
	 * @param q
	 * @return feature vector leaving out a slot for the class variable, i.e.,
	 *         the QA system that can answer this feature vector
	 */
	public Instance analyze(String q) {
		// +1 to later add class attribute
		Instance tmpInstance = new Instance(fvWekaAttributes.size());
		// the feature adds itself to the instance
		for (IAnalyzer analyzer : analyzers) {
			Attribute attribute = analyzer.getAttribute();
			if (attribute.isNumeric()) {
				tmpInstance.setValue(attribute, (double) analyzer.analyze(q));
			} else if (attribute.isNominal() || attribute.isString()) {
				tmpInstance.setValue(attribute, (String) analyzer.analyze(q));
			}
		}

		return tmpInstance;

	}

	public Attribute getClassAttribute() {
		return fmeasureAtt;
	}

}