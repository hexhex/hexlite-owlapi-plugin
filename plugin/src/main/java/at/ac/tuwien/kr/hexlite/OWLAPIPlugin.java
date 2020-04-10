package at.ac.tuwien.kr.hexlite;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import at.ac.tuwien.kr.hexlite.api.ISolverContext;
import at.ac.tuwien.kr.hexlite.api.ISymbol;

public class OWLAPIPlugin implements IPlugin {
    private static final Logger LOGGER = LogManager.getLogger("Hexlite-OWLAPIPlugin");

    private final AbstractMap<String, IOntologyContext> cachedContexts;

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

    public static abstract class BaseAtom implements IPluginAtom {
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
            LOGGER.debug("querying ontology with expression {}", () -> owlquery);
            oc.reasoner().getInstances(owlquery, false).entities().forEach(instance -> {
                LOGGER.debug("found instance {}", () -> instance);
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

    public abstract static class ModifiedOntologyBaseAtom extends BaseAtom {
        protected static class ModificationsContainer {
            public HashSet<ISymbol> nogood;
            public List<OWLOntologyChange> changes;

            public ModificationsContainer() {
                changes = new LinkedList<ISymbol>();
                nogood = new HashSet<ISymbol>();       
            }
        }

        private static List<InputType> prepareArguments(final List<InputType> _extraArgumentTypes) {
            final ArrayList<InputType> ret = new ArrayList<InputType>();
            ret.add(InputType.PREDICATE);
            ret.add(InputType.CONSTANT);
            ret.addAll(_extraArgumentTypes);
            return ret;
        }

        public ModifiedOntologyBaseAtom(final String _predicate, final List<InputType> _extraArgumentTypes, int output_arguments) {
            super(_predicate, prepareArguments(_extraArgumentTypes), output_arguments);
            // first argument = ontology meta file location (from BaseAtom)
            // second argument = delta predicate
            // third argument = delta selector
            // remaining arguments = _extraArgumentTypes from superclass
            // no output arguments (=0)
        }

        @Override
        public IAnswer retrieve(final ISolverContext ctx, final IQuery query) {
            final String location = withoutQuotes(query.getInput().get(0).value());
            LOGGER.info("{} retrieving with ontoURI={}", () -> getPredicate(), () -> location);
            final IOntologyContext oc = ontologyContext(location);
            final ISymbol delta_pred = query.getInput().get(1);
            final ISymbol delta_sel = query.getInput().get(2);
            final ModificationsContainer ontology_mods = extractModifications(
                ctx, query.getInterpretation(), delta_pred, delta_sel);
            LOGGER.info("applying changes ",ontology_mods.changes.toString());
            oc.applyChanges(ontology_mods.changes);
            try {
                return retrieveDetail(ctx, query, oc, ontology_mods.nogood);
            } finally {
                LOGGER.info("reverting changes");
                oc.revertChanges(modifications);
            }
        }

        public abstract Answer retrieveDetail(final ISolverContext ctx, final IQuery query, final IOntologyContext moc, final HashSet<ISymbol> nogood);

        protected ModificationsContainer extractModifications(IOntologyContext ctx, IInterpretation interpretation, ISymbol delta_pred, ISymbol delta_sel) {
            final ModificationsContainer ret = new ModificationsContainer();
            for(ISymbol atm : in.getInputAtoms()) {
                final ArrayList<ISymbol> atuple = atm.tuple();
                System.err.println("input atom "+atm.toString()+" with tuple "+atuple.toString());
                if( atuple.get(0) == delta_pred && atuple.get(2) == delta_sel ) {
                    System.err.println("  relevant with truth value "+atm.isTrue().toString());
                    if( atm.isTrue() ) {
                        ret.nogood.add(atm);
                        List<? extends ISymbol> childtuple = atuple.get(1).tuple();
                        extractSingleModification(ctx, childtuple, ret);
                    } else {
                        ret.nogood.add(atm.negate());
                    }
                }
            }
            return ret;
        }

        protected void extractSingleModification(IOntologyContext ctx, List<? extends ISymbol> child, ModificationsContainer out) {
            // alias for historical reasons
            final List<OWLOntologyChange> ret = out.changes;

            String mtype = child.get(0).value();
            List<IRI> argumentIRIs = new ArrayList<IRI>(child.size()-1);
            for( ISymbol arg : child.subList(1,child.size()) ) {
                argumentIRIs.add(IRI.create(ctx.expandNamespace(withoutQuotes(arg.value()))));
            }
            //LOGGER.info(" argumentIRIs = "+argumentIRIs);
            switch (mtype) {
            case "addc":
                ret.add(new AddAxiom(ctx.ontology(),
                        ctx.df().getOWLClassAssertionAxiom(
                            ctx.df().getOWLClass(argumentIRIs.get(0)),
                            ctx.df().getOWLNamedIndividual(argumentIRIs.get(1)))));
                break;
            case "delc":
                ret.add(new RemoveAxiom(ctx.ontology(),
                        ctx.df().getOWLClassAssertionAxiom(
                            ctx.df().getOWLClass(argumentIRIs.get(0)),
                            ctx.df().getOWLNamedIndividual(argumentIRIs.get(1)))));
                break;
            case "addop":
                ret.add(new AddAxiom(ctx.ontology(),
                        ctx.df().getOWLObjectPropertyAssertionAxiom(
                            ctx.df().getOWLObjectProperty(argumentIRIs.get(0)),
                            ctx.df().getOWLNamedIndividual(argumentIRIs.get(1)),
                            ctx.df().getOWLNamedIndividual(argumentIRIs.get(2)))));
                break;
            case "delop":
                ret.add(new RemoveAxiom(ctx.ontology(),
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
                        catch(RuntimeException e) {
                            LOGGER.info("  IT IS stringsymbol");
                            literal = ctx.df().getOWLLiteral(svalue);
                        }
                    }                            
                    ret.add(new AddAxiom(ctx.ontology(),
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
        }
    }

    public class ModifiedOntologyConsistentAtom extends ModifiedOntologyBaseAtom {
        public ModifiedOntologyConsistentAtom() {
            // dlConsistent[ontospec,deltapredicate,selector]
            // true iff the specified ontology after modification by
            //          the delta in deltapredicate selected by the selector
            //          is consistent
            super("dlConsistent", new ArrayList<InputType>(), 0);
        }

        @Override
        public Answer retrieveDetail(final ISolverContext ctx, final IQuery query, final IOntologyContext moc, final HashSet<ISymbol> nogood) {
            OWLReasoner reasoner = moc.reasoner();
            LOGGER.info("result: consistent="+reasoner.isConsistent());
            final ArrayList<ISymbol> emptytuple = new ArrayList<ISymbol>();

            final Answer answer = new Answer();
            if( reasoner.isConsistent() ) {
                answer.output(emptytuple);
                nogood.add(ctx.storeOutputAtom(emptytuple).negate());
            } else {
                nogood.add(ctx.storeOutputAtom(emptytuple);
            }
            ctx.learn(nogood);
            return answer;
        }
    }

    public class ModifiedOntologyClassQueryAtom extends ModifiedOntologyBaseAtom {
        public ModifiedOntologyClassQueryAtom() {
            super("dlC", Arrays.asList(new InputType[] { InputType.CONSTANT }), 1);
        }

        @Override
        public Answer retrieveDetail(final ISolverContext ctx, final IQuery query, final IOntologyContext moc, final HashSet<ISymbol> nogood) {
            OWLReasoner reasoner = moc.reasoner();
            LOGGER.info("result: consistent="+reasoner.isConsistent());
            final ArrayList<ISymbol> emptytuple = new ArrayList<ISymbol>();

            final Answer answer = new Answer();
            if( !oc.reasoner().isConsistent() ) {
                // make this atom false
                // XXX is this a good idea? logic would say it is true
                // cannot learn because do not know potential output tuples of this external atom
                return answer;
            }

            final String opQuery = withoutQuotes(query.getInput().get(3).value());
            final String expandedQuery = moc.expandNamespace(opQuery);
            LOGGER.debug("expanded query to {}", () -> expandedQuery);
            final OWLClassExpression cquery = moc.df().getOWLClass(IRI.create(expandedQuery));
            LOGGER.debug("querying ontology with expression {}", () -> cquery);
            moc.reasoner()
                .getInstances(cquery, false /*get also direct instances*/)
                .entities()
                .forEach(domainindividual -> {
                    LOGGER.debug("found individual {} in query {}", () -> domainindividual, () -> cquery);
                    HashSet<ISymbol> here_nogood = new HashSet<ISymbol>(nogood);

                    final ArrayList<ISymbol> t = new ArrayList<ISymbol>(1);
                    t.add(ctx.storeString(domainindividual.getIRI().toString()));

                    answer.output(t);
                    here_nogood.add(ctx.storeOutputAtom(t).negate());
                    ctx.learn(here_nogood);
                });
            return answer;
        }

    public class ModifiedOntologyObjectPropertyQueryAtom extends ModifiedOntologyBaseAtom {
        public ModifiedOntologyObjectPropertyQueryAtom() {
            super("dlOP", Arrays.asList(new InputType[] { InputType.CONSTANT }), 2);
        }

        @Override
        public IAnswer retrieve(final ISolverContext ctx, final IQuery query) {
            final String location = withoutQuotes(query.getInput().get(0).value());
            LOGGER.info("{} retrieving with ontoURI={}", () -> getPredicate(), () -> location);
            final IOntologyContext oc = ontologyContext(location);

            final Set<? extends List<ISymbol>> delta_ext = query.getInput().get(1).extension();
            final ISymbol delta_sel = query.getInput().get(2);
            final List<? extends OWLOntologyChange> modifications = extractModifications(oc, delta_ext, delta_sel);

            final Answer answer = new Answer();

            LOGGER.info("applying changes ",modifications.toString());
            oc.applyChanges(modifications);
            try {
                if( !oc.reasoner().isConsistent() )
                    return answer;

                final String opQuery = withoutQuotes(query.getInput().get(3).value());
                final String expandedQuery = oc.expandNamespace(opQuery);
                LOGGER.debug("expanded query to {}", () -> expandedQuery);

                final OWLObjectProperty op = oc.df().getOWLObjectProperty(IRI.create(expandedQuery));
                LOGGER.debug("querying ontology with expression {}", () -> op);
                oc.reasoner().objectPropertyDomains(op).flatMap(domainclass -> oc.reasoner().instances(domainclass, false))
                        .distinct().forEach(domainindividual -> {
                            oc.reasoner().objectPropertyValues(domainindividual, op).forEach(value -> {
                                LOGGER.debug("found individual {} related via {} to individual {}", () -> domainindividual,
                                        () -> op, () -> value);
                                final ArrayList<ISymbol> t = new ArrayList<ISymbol>(2);
                                t.add(ctx.storeString(domainindividual.getIRI().toString()));
                                t.add(ctx.storeString(value.getIRI().toString()));
                                answer.output(t);
                            });
                        });
            } finally {
                LOGGER.info("reverting changes");
                oc.revertChanges(modifications);
            }

            return answer;
        }
    }

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
        atoms.add(new ModifiedOntologyConsistentAtom());
        atoms.add(new ModifiedOntologyClassQueryAtom());
        atoms.add(new ModifiedOntologyObjectPropertyQueryAtom());
        atoms.add(new SimplifyIRIAtom());
        return atoms;        
	}
}