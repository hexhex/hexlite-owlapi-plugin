package at.ac.tuwien.kr.hexlite;

import java.util.AbstractCollection;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import at.ac.tuwien.kr.hexlite.api.Answer;
import at.ac.tuwien.kr.hexlite.api.ExtSourceProperties;
import at.ac.tuwien.kr.hexlite.api.IPlugin;
import at.ac.tuwien.kr.hexlite.api.IPluginAtom;
import at.ac.tuwien.kr.hexlite.api.IPluginAtom.InputType;
import at.ac.tuwien.kr.hexlite.api.ISolverContext;
import at.ac.tuwien.kr.hexlite.api.ISolverContext.StoreAtomException;
import at.ac.tuwien.kr.hexlite.api.ISymbol;
import at.ac.tuwien.kr.hexlite.api.IInterpretation;

public class OWLAPIPlugin implements IPlugin {
    private static final Logger LOGGER = LogManager.getLogger("HexOWLAPI");

    private final Map<String, IOntologyContext> cachedContexts;

    public OWLAPIPlugin() {
        cachedContexts = new HashMap<String, IOntologyContext>();
    }

    // @Override
    public IOntologyContext ontologyContext(final String ontolocation) {
        if (!cachedContexts.containsKey(ontolocation)) {
            cachedContexts.put(ontolocation, new OntologyContext(ontolocation));
        }
        return cachedContexts.get(ontolocation);
    }

    public static List<InputType> prepareArguments(final List<InputType> _extraArgumentTypes) {
        final ArrayList<InputType> ret = new ArrayList<InputType>();
        ret.add(InputType.PREDICATE);
        ret.add(InputType.CONSTANT);
        ret.addAll(_extraArgumentTypes);
        return ret;
    }
    
    public abstract class BaseAtom implements IPluginAtom {
        private final String predicate;
        private final ArrayList<InputType> inputArguments;
        private final int outputArguments;
        private final ExtSourceProperties properties;

        public BaseAtom(final String _predicate, final List<InputType> _extraArgumentTypes,
                final int _outputArguments) {
            // first argument = ontology meta file location
            predicate = _predicate;
            inputArguments = new ArrayList<InputType>();
            inputArguments.add(InputType.CONSTANT);
            for (final InputType arg : _extraArgumentTypes) {
                inputArguments.add(arg);
            }
            outputArguments = _outputArguments;
            properties = new ExtSourceProperties();
            // this prevents hints to the solver in case of false external computations (unseen output constants)
            // -> do not do this!
            //properties.setDoInputOutputLearning(false);
        }

        @Override
        public String getPredicate() {
            return predicate;
        }

        @Override
        public ArrayList<InputType> getInputArguments() {
            return inputArguments;
        }

        @Override
        public int getOutputArguments() {
            return outputArguments;
        }

        @Override
        public ExtSourceProperties getExtSourceProperties() {
            return properties;
        }

        protected String withoutQuotes(final String s) {
            if (s.startsWith("\"") && s.endsWith("\""))
                return s.substring(1, s.length() - 1);
            else
                return s;
        }
    }

    public class ClassQueryReadOnlyAtom extends BaseAtom {
        public ClassQueryReadOnlyAtom() {
            super("dlCro", Arrays.asList(new InputType[] { InputType.CONSTANT }), 1);
        }

        @Override
        public IAnswer retrieve(final ISolverContext ctx, final IQuery query) {
            LOGGER.debug("retrieve of {}", () -> getPredicate());
            final String location = withoutQuotes(query.getInput().get(0).value());
            final String conceptQuery = withoutQuotes(query.getInput().get(1).value());
            LOGGER.info("{} retrieving with ontoURI={} and query {}", () -> getPredicate(), () -> location,
                    () -> conceptQuery);
            final IOntologyContext oc = ontologyContext(location);
            final String expandedQuery = oc.expandNamespace(conceptQuery);
            LOGGER.debug("expanded query to {}", () -> expandedQuery);

            final Answer answer = new Answer();
            final OWLClassExpression owlquery = oc.df().getOWLClass(IRI.create(expandedQuery));
            LOGGER.info("querying unmodified ontology with expression {}", () -> owlquery);
            oc.reasoner().getInstances(owlquery, false).entities().forEach(instance -> {
                LOGGER.info("found instance {}", () -> instance);
                final ArrayList<ISymbol> t = new ArrayList<ISymbol>(1);
                t.add(ctx.storeString(instance.getIRI().toString()));
                answer.output(t);
            });

            return answer;
        }
    }

    public class ObjectPropertyReadOnlyQueryAtom extends BaseAtom {
        public ObjectPropertyReadOnlyQueryAtom() {
            super("dlOPro", Arrays.asList(new InputType[] { InputType.CONSTANT }), 2);
        }

        @Override
        public IAnswer retrieve(final ISolverContext ctx, final IQuery query) {
            LOGGER.debug("retrieve of {}", () -> getPredicate());
            final String location = withoutQuotes(query.getInput().get(0).value());
            final String opQuery = withoutQuotes(query.getInput().get(1).value());
            LOGGER.info("{} retrieving with ontoURI={} and query {}", () -> getPredicate(), () -> location,
                    () -> opQuery);
            final IOntologyContext oc = ontologyContext(location);
            final String expandedQuery = oc.expandNamespace(opQuery);
            LOGGER.debug("expanded query to {}", () -> expandedQuery);

            final Answer answer = new Answer();
            final OWLObjectProperty op = oc.df().getOWLObjectProperty(IRI.create(expandedQuery));
            LOGGER.debug("querying ontology with expression {}", () -> op);
            oc.reasoner().objectPropertyDomains(op)
                .flatMap( domainclass -> oc.reasoner().instances(domainclass, false) )
                .distinct()
                .forEach( domainindividual -> {
                    oc.reasoner().objectPropertyValues(domainindividual, op).forEach( value -> {
                        LOGGER.debug("found individual {} related via {} to individual {}", () -> domainindividual, () -> op, () -> value);
                        final ArrayList<ISymbol> t = new ArrayList<ISymbol>(2);
                        t.add(ctx.storeString(domainindividual.getIRI().toString())); // maybe getShortForm()
                        t.add(ctx.storeString(value.getIRI().toString())); // maybe getShortForm()
                        answer.output(t);
                    });
                });

            return answer;
        }
    }

    public class DataPropertyReadOnlyQueryAtom extends BaseAtom {
        public DataPropertyReadOnlyQueryAtom() {
            super("dlDPro", Arrays.asList(new InputType[] { InputType.CONSTANT }), 2);
        }

        @Override
        public IAnswer retrieve(final ISolverContext ctx, final IQuery query) {
            LOGGER.debug("retrieve of {}", () -> getPredicate());
            final String location = withoutQuotes(query.getInput().get(0).value());
            final String dpQuery = withoutQuotes(query.getInput().get(1).value());
            LOGGER.info("{} retrieving with ontoURI={} and query {}", () -> getPredicate(), () -> location, () -> dpQuery);
            final IOntologyContext oc = ontologyContext(location);
            final String expandedQuery = oc.expandNamespace(dpQuery);
            LOGGER.debug("expanded query to {}", () -> expandedQuery);

            final Answer answer = new Answer();
            final OWLDataProperty dp = oc.df().getOWLDataProperty(IRI.create(expandedQuery));
            LOGGER.debug("querying ontology with expression {}", () -> dp);
            oc.reasoner().dataPropertyDomains(dp)
                .flatMap( domainclass -> oc.reasoner().instances(domainclass, false) )
                .distinct()
                .forEach( domainindividual -> {
                    oc.reasoner().dataPropertyValues(domainindividual, dp).forEach( value -> {
                        LOGGER.debug("found individual {} related via data property {} to value {}", () -> domainindividual, () -> dp, () -> value);
                        final ArrayList<ISymbol> t = new ArrayList<ISymbol>(2);
                        t.add(ctx.storeString(domainindividual.getIRI().toString())); // maybe getShortForm()
                        t.add(ctx.storeString(value.getLiteral())); // maybe deal with integers/types differently: value.isBoolean value.isInteger
                        answer.output(t);
                    });
                });
            
            return answer;
        }
    }

    public abstract class ModifiedOntologyBaseAtom extends BaseAtom {
        protected class ModificationsContainer {
            public ISymbol onto;
            public ISymbol predicate;
            public HashMap<ISymbol, HashSet<ISymbol> > nogoodBySelector;
            public List<OWLOntologyChange> changes;

            public ModificationsContainer(ISymbol onto, ISymbol predicate) {
                onto = onto;
                predicate = predicate;
                changes = new LinkedList<OWLOntologyChange>();
                nogoodBySelector = new HashMap<ISymbol, HashSet<ISymbol> >();
            }

            public void addToNogood(ISymbol selector, ISymbol literalToAdd) {
                if( !nogoodBySelector.containsKey(selector) ) {
                    nogoodBySelector.put(selector, new HashSet<ISymbol>());                    
                }
                nogoodBySelector.get(selector).add(literalToAdd);
            }

            public void generateNogoodsForOutput(ISolverContext ctx, int output_index, ISymbol output) {
                // go over all potential output atoms and check if we have nogoods
                for( final ISymbol output_atom : ctx.getInstantiatedOutputAtoms() ) {
                    final ArrayList<ISymbol> output_tuple = output_atom.tuple();
                    if( output_tuple.get(0) != onto || output_tuple.get(1) != predicate || output_tuple.get(output_index) != output )
                        continue;
                    final ISymbol selector = output_tuple.get(2);
                    if( nogoodBySelector.containsKey(selector) ) {
                        LOGGER.info("generateNogoodsForOutput for output ", output.toString(), " in index ", output_index, " found nogood for selector ", selector.toString());
                        // maybe we don't need to copy, but let's stay on the safe side
                        final HashSet<ISymbol> nogood = new HashSet<ISymbol>(nogoodBySelector.get(selector));
                        nogood.add(output_atom.negate());
                        ctx.learn(nogood);
                    }
                }
            }
        }

        public ModifiedOntologyBaseAtom(final String _predicate, final List<InputType> _extraArgumentTypes, final int output_arguments) {
            super(_predicate, prepareArguments(_extraArgumentTypes), output_arguments);
            // first argument = ontology meta file location (from BaseAtom)
            // second argument = delta predicate
            // third argument = delta selector
            // remaining arguments = _extraArgumentTypes from superclass
            // no output arguments (=0)
        }

        @Override
        public IAnswer retrieve(final ISolverContext ctx, final IQuery query) {
            final ISymbol onto = query.getInput().get(0);
            final String location = withoutQuotes(onto.value());
            //LOGGER.info("{} retrieving with ontoURI={}", () -> getPredicate(), () -> location);
            final IOntologyContext oc = ontologyContext(location);
            final ISymbol delta_pred = query.getInput().get(1);
            final ISymbol delta_sel = query.getInput().get(2);
            final ModificationsContainer ontology_mods = extractModifications(
                oc, query.getInterpretation(), onto, delta_pred, delta_sel);
            //LOGGER.info("applying changes ",ontology_mods.changes.toString());
            oc.applyChanges(ontology_mods.changes);
            try {
                return retrieveDetail(ctx, query, oc, ontology_mods);
            } finally {
                //LOGGER.info("reverting changes");
                oc.revertChanges(ontology_mods.changes);
            }
        }

        public abstract Answer retrieveDetail(final ISolverContext ctx, final IQuery query, final IOntologyContext moc, final ModificationsContainer modcontainer);

        protected ModificationsContainer extractModifications(final IOntologyContext ctx, final IInterpretation interpretation, final ISymbol onto, final ISymbol delta_pred, final ISymbol delta_sel) {
            // here we get all relevant input atoms for this ground instantiation
            // therefore we can only collect nogoods for calls with the same predicate inputs and with the same or different constant inputs
            // we collect one modification of the ontology based on 
            // * current predicate input (predicate, 1st argument)
            // * current constant input (selector, 2nd argument)
            // we create a nogood for this input
            // we also create all alternative nogoods for all alternative selectors (2nd argument)
            //
            // we do this in 2 passes:
            // pass 1:
            // * use actual selector and selection
            // * build modification
            // * remember tuples and their polarity
            // pass 2:
            // * use tuples and polarity to build nogoods for all selectors

            final HashSet<ArrayList<ISymbol> > positiveTuples = new HashSet<ArrayList<ISymbol> >();
            final HashSet<ArrayList<ISymbol> > negativeTuples = new HashSet<ArrayList<ISymbol> >();
            final ModificationsContainer ret = new ModificationsContainer(onto, delta_pred);

            // pass 1: record positive/negative tuples and extract modification
            for(final ISymbol atm : interpretation.getInputAtoms()) {
                // going over all instantiated relevant input atoms, also those that are false

                final ArrayList<ISymbol> atuple = atm.tuple();
                LOGGER.info("pass 1 input atom {} with tuple {}", () -> atm.toString(), () -> atuple.toString());
                if( atuple.get(0).equals(delta_pred) && atuple.get(1).equals(delta_sel) ) {
                    LOGGER.info("..is relevant with truth value {}", () -> atm.isTrue());
                    final ArrayList<ISymbol> childtuple = atuple.get(2).tuple();

                    // atm is always represented as positive, so if the truth value is negative we must add its negated literal
                    if( atm.isTrue() ) {
                        positiveTuples.add(childtuple);
                        extractSingleModification(ctx, childtuple, ret);
                    } else {
                        // no modification, but the result we compute still depends on the falsity of atm
                        negativeTuples.add(childtuple);
                    }
                } else {
                    LOGGER.info("..is irrelevant for delta_pred {} and delta_sel {}", () -> delta_pred.toString(), () -> delta_sel.toString());
                }
            }

            // pass 2: build all nogoods of all selectors with same polarity as above
            for(final ISymbol atm : interpretation.getInputAtoms()) {
                final ArrayList<ISymbol> atuple = atm.tuple();
                LOGGER.info("pass 2 input atom {} with tuple {}", () -> atm.toString(), () -> atuple.toString());
                if( atuple.get(0).equals(delta_pred) ) {
                    final ISymbol selector = atuple.get(1);
                    final ArrayList<ISymbol> childtuple = atuple.get(2).tuple();
                    if( positiveTuples.contains(childtuple) ) {
                        ret.addToNogood(selector, atm);
                    } else if( negativeTuples.contains(childtuple) ) {
                        ret.addToNogood(selector, atm.negate());
                    }
                }
            }

            return ret;
        }

        protected void extractSingleModification(final IOntologyContext ctx, final List<? extends ISymbol> child, final ModificationsContainer out) {
            final String mtype = child.get(0).value();
            final List<IRI> argumentIRIs = new ArrayList<IRI>(child.size()-1);
            for( final ISymbol arg : child.subList(1,child.size()) ) {
                argumentIRIs.add(IRI.create(ctx.expandNamespace(withoutQuotes(arg.value()))));
            }
            //LOGGER.info(" argumentIRIs = "+argumentIRIs);
            final List<OWLOntologyChange> deletes = new ArrayList<OWLOntologyChange>();
            final List<OWLOntologyChange> adds = new ArrayList<OWLOntologyChange>();
            switch (mtype) {
            case "addc":
                adds.add(new AddAxiom(ctx.ontology(),
                        ctx.df().getOWLClassAssertionAxiom(
                            ctx.df().getOWLClass(argumentIRIs.get(0)),
                            ctx.df().getOWLNamedIndividual(argumentIRIs.get(1)))));
                break;
            case "delc":
                deletes.add(new RemoveAxiom(ctx.ontology(),
                        ctx.df().getOWLClassAssertionAxiom(
                            ctx.df().getOWLClass(argumentIRIs.get(0)),
                            ctx.df().getOWLNamedIndividual(argumentIRIs.get(1)))));
                break;
            case "addop":
                adds.add(new AddAxiom(ctx.ontology(),
                        ctx.df().getOWLObjectPropertyAssertionAxiom(
                            ctx.df().getOWLObjectProperty(argumentIRIs.get(0)),
                            ctx.df().getOWLNamedIndividual(argumentIRIs.get(1)),
                            ctx.df().getOWLNamedIndividual(argumentIRIs.get(2)))));
                break;
            case "delop":
                deletes.add(new RemoveAxiom(ctx.ontology(),
                        ctx.df().getOWLObjectPropertyAssertionAxiom(
                            ctx.df().getOWLObjectProperty(argumentIRIs.get(0)),
                            ctx.df().getOWLNamedIndividual(argumentIRIs.get(1)),
                            ctx.df().getOWLNamedIndividual(argumentIRIs.get(2)))));
                break;
            case "adddp":
                {
                    // TODO implement other literal types besides string
                    final ISymbol symvalue = child.get(3);
                    final String svalue = symvalue.value();
                    OWLLiteral literal = null;
                    LOGGER.info("processing adddp value "+svalue);
                    if( svalue.startsWith("\"") ) {
                        LOGGER.info("  IT IS string");
                        literal = ctx.df().getOWLLiteral(svalue.substring(1,svalue.length()-1));
                    } else if( svalue.equals("true") ) {
                        LOGGER.info("  IT IS true");
                        literal = ctx.df().getOWLLiteral(true);
                    } else if( svalue.equals("false") ) {
                        LOGGER.info("  IT IS false");
                        literal = ctx.df().getOWLLiteral(false);
                    } else {
                        try {
                            literal = ctx.df().getOWLLiteral(symvalue.intValue());
                            LOGGER.info("  IT IS integer");
                        }
                        catch(final RuntimeException e) {
                            LOGGER.info("  IT IS stringsymbol");
                            literal = ctx.df().getOWLLiteral(svalue);
                        }
                    }                            
                    adds.add(new AddAxiom(ctx.ontology(),
                            ctx.df().getOWLDataPropertyAssertionAxiom(
                                ctx.df().getOWLDataProperty(argumentIRIs.get(0)),
                                ctx.df().getOWLNamedIndividual(argumentIRIs.get(1)),
                                literal)));
                }
                break;
            default:
                LOGGER.error("delta modification of ontology got unknown type '" + mtype
                        + "' (can be {add,del}{c,op,dp}) - ignoring");
            }
            out.changes.addAll(deletes);
            out.changes.addAll(adds);
        }        
    }

    // public class ModifiedOntologyConsistentAtom extends ModifiedOntologyBaseAtom {
    //     public ModifiedOntologyConsistentAtom() {
    //         // dlConsistent[ontospec,deltapredicate,selector]
    //         // true iff the specified ontology after modification by
    //         //          the delta in deltapredicate selected by the selector
    //         //          is consistent
    //         super("dlConsistent", new ArrayList<InputType>(), 0);
    //     }

    //     @Override
    //     public Answer retrieveDetail(final ISolverContext ctx, final IQuery query, final IOntologyContext moc, final HashSet<ISymbol> nogood) {
    //         final OWLReasoner reasoner = moc.reasoner();
    //         LOGGER.info("result: consistent={}", () -> reasoner.isConsistent());
    //         final ArrayList<ISymbol> emptytuple = new ArrayList<ISymbol>();

    //         final Answer answer = new Answer();
    //         boolean consistent = reasoner.isConsistent();
    //         if( consistent ) {
    //             answer.output(emptytuple);
    //         }

    //         try {
    //             if (consistent) {
    //                 nogood.add(ctx.storeOutputAtom(emptytuple).negate());
    //             } else {
    //                 nogood.add(ctx.storeOutputAtom(emptytuple));
    //             }
    //             ctx.learn(nogood);
    //         } catch(StoreAtomException e) {
    //             // ignore
    //             LOGGER.info("dlConsistent ignoring exception {}", () -> e.toString());
    //         }
    //         return answer;
    //     }
    // }

    public class ModifiedOntologyClassQueryAtom extends ModifiedOntologyBaseAtom {
        public ModifiedOntologyClassQueryAtom() {
            super("dlC", Arrays.asList(new InputType[] { InputType.CONSTANT }), 1);
        }

        //@Override
        public Answer retrieveDetail(final ISolverContext ctx, final IQuery query, final IOntologyContext moc, final ModificationsContainer modcon) {
            final OWLReasoner reasoner = moc.reasoner();
            final ArrayList<ISymbol> emptytuple = new ArrayList<ISymbol>();

            final Answer answer = new Answer();
            if( !moc.reasoner().isConsistent() ) {
                // make this atom false
                // XXX is this a good idea? logic would say it is true
                // cannot learn because do not know potential output tuples of this external atom
                LOGGER.info("result (dlC): inconsistent!");
                return answer;
            }

            final String cQuery = withoutQuotes(query.getInput().get(3).value());
            final String expandedQuery = moc.expandNamespace(cQuery);
            LOGGER.debug("expanded class query to {}", () -> expandedQuery);
            final OWLClassExpression cquery = moc.df().getOWLClass(IRI.create(expandedQuery));
            LOGGER.debug("querying ontology with expression {}", () -> cquery);
            moc.reasoner()
                .getInstances(cquery, false /*get also direct instances*/)
                .entities()
                .forEach(domainindividual -> {
                    LOGGER.debug("found individual {} in query {}", () -> domainindividual, () -> cquery);

                    final ISymbol trueOutput = ctx.storeString(domainindividual.getIRI().toString());
                    LOGGER.info("result (dlC): consistent and found {}", () -> domainindividual.getIRI().toString());

                    final ArrayList<ISymbol> t = new ArrayList<ISymbol>(1);
                    t.add(trueOutput);
                    answer.output(t);

                    // 4, because the output constant is the 5th element in the total replacement tuple [onto,delta,selector,query](output)
                    modcon.generateNogoodsForOutput(ctx, 4, trueOutput);
                });
            return answer;
        }
    }

    // public class ModifiedOntologyObjectPropertyQueryAtom extends ModifiedOntologyBaseAtom {
    //     public ModifiedOntologyObjectPropertyQueryAtom() {
    //         super("dlOP", Arrays.asList(new InputType[] { InputType.CONSTANT }), 2);
    //     }

    //     public Answer retrieveDetail(final ISolverContext ctx, final IQuery query, final IOntologyContext moc, final HashSet<ISymbol> nogood) {
    //         final OWLReasoner reasoner = moc.reasoner();
    //         final ArrayList<ISymbol> emptytuple = new ArrayList<ISymbol>();

    //         final Answer answer = new Answer();
    //         if( !moc.reasoner().isConsistent() ) {
    //             // make this atom false
    //             // XXX is this a good idea? logic would say it is true
    //             // cannot learn because do not know potential output tuples of this external atom
    //             LOGGER.info("result (dlOP): inconsistent");
    //             return answer;
    //         }

    //         final String opQuery = withoutQuotes(query.getInput().get(3).value());
    //         final String expandedQuery = moc.expandNamespace(opQuery);
    //         LOGGER.debug("expanded object property query to {}", () -> expandedQuery);
    //         final OWLObjectProperty op = moc.df().getOWLObjectProperty(IRI.create(expandedQuery));
    //         LOGGER.debug("querying ontology with expression {}", () -> op);
    //         moc.reasoner()
    //             .objectPropertyDomains(op)
    //             .flatMap(domainclass -> moc.reasoner().instances(domainclass, false))
    //             .distinct().forEach(domainindividual -> {
    //                 moc.reasoner()
    //                     .objectPropertyValues(domainindividual, op)
    //                     .forEach(value -> {
    //                         LOGGER.debug("found individual {} related via {} to individual {}", () -> domainindividual,
    //                                 () -> op, () -> value);
    //                         final ArrayList<ISymbol> t = new ArrayList<ISymbol>(2);
    //                         t.add(ctx.storeString(domainindividual.getIRI().toString()));
    //                         t.add(ctx.storeString(value.getIRI().toString()));
                            
    //                         LOGGER.info("result (dlOP): consistent and found {}/{}", () -> domainindividual.getIRI().toString(), () -> value.getIRI().toString());
    //                         answer.output(t);

    //                         try {
    //                             HashSet<ISymbol> here_nogood = new HashSet<ISymbol>(nogood);
    //                             here_nogood.add(ctx.storeOutputAtom(t).negate());
    //                             ctx.learn(here_nogood);                           
    //                         } catch(StoreAtomException e) {
    //                             // ignore
    //                             LOGGER.info("dlOP ignoring exception {}", () -> e.toString());
    //                         }
    //                     });
    //             });
    //         return answer;
    //     }
    // }

    public class SimplifyIRIAtom extends BaseAtom {
        public SimplifyIRIAtom() {
            super("dlSimplifyIRI", Arrays.asList(new InputType[] { InputType.CONSTANT }), 1);
        }

        @Override
        public IAnswer retrieve(final ISolverContext ctx, final IQuery query) {
            LOGGER.debug("retrieve of {}", () -> getPredicate());
            final String location = withoutQuotes(query.getInput().get(0).value());
            final String iri = withoutQuotes(query.getInput().get(1).value());
            LOGGER.info("{} retrieving with ontoURI={} and query {}", () -> getPredicate(), () -> location, () -> iri);
            final IOntologyContext oc = ontologyContext(location);
            final String simplified = oc.simplifyNamespaceIfPossible(iri);
            LOGGER.debug("simplified to {}", () -> simplified);

            final Answer answer = new Answer();
            final ArrayList<ISymbol> t = new ArrayList<ISymbol>(1);
            t.add(ctx.storeString(simplified));
            answer.output(t);
            
            return answer;
        }
    }

    @Override
    public String getName() {
        return "OWLAPIPlugin";
    }

    @Override
    public AbstractCollection<IPluginAtom> createAtoms() {
        final LinkedList<IPluginAtom> atoms = new LinkedList<IPluginAtom>();
        atoms.add(new ClassQueryReadOnlyAtom());
        atoms.add(new ObjectPropertyReadOnlyQueryAtom());
        atoms.add(new DataPropertyReadOnlyQueryAtom());
        //atoms.add(new ModifiedOntologyConsistentAtom());
        atoms.add(new ModifiedOntologyClassQueryAtom());
        //atoms.add(new ModifiedOntologyObjectPropertyQueryAtom());
        atoms.add(new SimplifyIRIAtom());
        return atoms;        
	}
}
