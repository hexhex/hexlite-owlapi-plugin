package at.ac.tuwien.kr.hexlite;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.ChangeApplied;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.util.AutoIRIMapper;
import org.semanticweb.owlapi.model.parameters.OntologyCopy;

class OntologyContext implements IOntologyContext {
   private static final Logger LOGGER = LogManager.getLogger("Hexlite-OWLAPIPlugin");
   
   String _uri;
   HashMap<String, String> _namespaces;
   OWLDataFactory _df;
   OWLOntologyManager _manager;
   OWLOntology _ontology;
   OWLReasoner _reasoner;

   private String extendURI(final String uri) {
       if (uri.indexOf("://") == -1) {
           if (uri.startsWith("/")) {
               return "file://" + uri;
           } else {
               return "file://" + System.getProperty("user.dir") + "/" + uri;
           }
       } else {
           return uri;
       }
   }

   private JSONObject loadMetaFile(final String metafile) {
       final JSONParser parser = new JSONParser();

       try {
           return (JSONObject) parser.parse(new FileReader(metafile));
       } catch (final IOException io) {
           LOGGER.error("error loading JSON from " + metafile, io);
       } catch (final ParseException pe) {
           LOGGER.error("error loading JSON from " + metafile, pe);
       }
       return null;
   }

   public OntologyContext(final String metafile) {
       final JSONObject meta = loadMetaFile(metafile);

       _uri = extendURI((String) meta.get("load-uri"));
       _namespaces = new HashMap<String, String>();
       if (meta.containsKey("namespaces")) {
           final JSONObject nsobject = (JSONObject) meta.get("namespaces");
           for (final Object ok : nsobject.keySet()) {
               if (ok instanceof String) {
                   final String k = (String) ok;
                   final Object ov = nsobject.get(ok);
                   if (ov instanceof String) {
                       _namespaces.put(k, (String) ov);
                   } else {
                       LOGGER.error("namespaces must have String values, skipping {} / {}", () -> k, () -> ov);
                   }
               } else {
                   LOGGER.error("namespaces must have String keys, skipping {}", () -> ok);
               }
           }
       }
       _df = OWLManager.getOWLDataFactory();
       _manager = OWLManager.createOWLOntologyManager();

       // make dependency ontologies auto-loadable from current directory
       final File file = new File(System.getProperty("user.dir"));
       _manager.getIRIMappers().add(new AutoIRIMapper(file, true));

       try {
           _ontology = _manager.loadOntology(IRI.create(_uri));
       } catch (final OWLOntologyCreationException e) {
           System.err.println("could not load ontology " + _uri + " with exception " + e.toString());
       }
       // adding here works
    //    org.semanticweb.owlapi.model.parameters.ChangeApplied ca = _ontology.addAxiom(
    //     _df.getOWLClassAssertionAxiom(
    //         _df.getOWLClass(
    //             "http://protege.stanford.edu/plugins/owl/owl-library/koala.owl#Quokka"
    //         ), _df.getOWLNamedIndividual(
    //             "http://kr.tuwien.ac.at/hexlite/hexlite-plugin-owlapi/examples/koala-extended.owl#lisa"
    //         )));
    //    LOGGER.info("ZZ  -> "+ca+" yields "+_ontology);

        _reasoner = null;
   }

   public OWLDataFactory df() {
       return _df;
   }

   public OWLOntologyManager manager() {
       return _manager;
   }

   public OWLReasoner reasoner() {
       if( true || _reasoner == null ) {
           // this reasoner is not sufficient
           //OWLReasonerFactory rf = new StructuralReasonerFactory();
           //_reasoner = rf.createNonBufferingReasoner(_ontology);
           //_reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);

           // this works
           org.semanticweb.HermiT.Configuration cfg = new org.semanticweb.HermiT.Configuration();
           _reasoner = new org.semanticweb.HermiT.Reasoner(cfg, _ontology);


        //    org.semanticweb.owlapi.model.parameters.ChangeApplied ca = _ontology.addAxiom(
        //     _df.getOWLClassAssertionAxiom(
        //         _df.getOWLClass(
        //             "http://protege.stanford.edu/plugins/owl/owl-library/koala.owl#Quokka"
        //         ), _df.getOWLNamedIndividual(
        //             "http://kr.tuwien.ac.at/hexlite/hexlite-plugin-owlapi/examples/koala-extended.owl#lisa"
        //         )));
        //    LOGGER.info("ZZ  -> "+ca+" yields "+_ontology);
    
           // this works
           //_reasoner = new org.semanticweb.HermiT.Reasoner.ReasonerFactory().createReasoner(_ontology);

           // this is not necessary
           //_reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
           LOGGER.warn("created hermit which is consistent:" +_reasoner.isConsistent());
       }
       return _reasoner;
   }

   public OWLOntology ontology() {
       return _ontology;
   }

   public String namespace(final String key) {
       return _namespaces.get(key);
   }

   public String expandNamespace(final String value) {
       final int idx = value.indexOf(":");
       if (idx == -1 || (value.length() > idx && value.charAt(idx + 1) == '/')) {
           // no namespace
           return value;
       } else {
           final String prefix = value.substring(0, idx);
           final String suffix = value.substring(idx + 1);
           LOGGER.debug("expandNamespace got prefix " + prefix + " and suffix " + suffix);
           String ret = value;
           if (_namespaces.containsKey(prefix)) {
               ret = namespace(prefix) + suffix;
           } else {
               LOGGER.warn("encountered unknown prefix " + prefix);
           }
           LOGGER.debug("expandNamespace changed " + value + " to " + ret);
           return ret;
       }
   }

   public String simplifyNamespaceIfPossible(final String value) {
       for (final Map.Entry<String, String> entry : _namespaces.entrySet()) {
           if (value.startsWith(entry.getValue())) {
               return entry.getKey() + ":" + value.substring(entry.getValue().length());
           }
       }
       return value;
    }

    public void applyChanges(List<? extends OWLOntologyChange> changes) {
        ChangeApplied ca = _manager.applyChanges(changes);
        LOGGER.info("applyChange "+ca+" for "+changes.toString());
    }
    public void revertChanges(List<? extends OWLOntologyChange> changes) {
        List<OWLOntologyChange> reversed = new ArrayList<OWLOntologyChange>(changes.size());
        for(OWLOntologyChange c : changes) {
            reversed.add(c.reverseChange());
        }
        ChangeApplied ca = _manager.applyChanges(reversed);
        LOGGER.info("revertChange "+ca+" for "+changes.toString());
    }
}