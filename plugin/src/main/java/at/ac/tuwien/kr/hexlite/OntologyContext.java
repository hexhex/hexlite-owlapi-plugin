package at.ac.tuwien.kr.hexlite;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

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
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.AutoIRIMapper;

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
        
        _reasoner = null;
    }
    
    public OWLDataFactory df() {
        return _df;
    }
    
    public OWLOntologyManager manager() {
        return _manager;
    }
    
    public OWLReasoner reasoner() {
        if( _reasoner == null ) {
            // StructuralReasoner is not sufficient
            
            org.semanticweb.HermiT.Configuration cfg = new org.semanticweb.HermiT.Configuration();
            _reasoner = new org.semanticweb.HermiT.Reasoner(cfg, _ontology);
            if( !_reasoner.isConsistent() ) LOGGER.warn("created inconsistent reasoner: check base ontology");
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
            //LOGGER.debug("expandNamespace got prefix " + prefix + " and suffix " + suffix);
            String ret = value;
            if (_namespaces.containsKey(prefix)) {
                ret = namespace(prefix) + suffix;
            } else {
                LOGGER.warn("encountered unknown prefix " + prefix);
            }
            //LOGGER.debug("expandNamespace changed " + value + " to " + ret);
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
        _manager.applyChanges(changes);
        //ChangeApplied ca = _manager.applyChanges(changes);
        //LOGGER.info("applyChange "+ca+" for "+changes.toString());
        // synchronize reasoner
        if( _reasoner != null ) _reasoner.flush();
    }
    public void revertChanges(List<? extends OWLOntologyChange> changes) {
        List<OWLOntologyChange> reversed = new ArrayList<OWLOntologyChange>(changes.size());
        for(OWLOntologyChange c : changes) {
            reversed.add(c.reverseChange());
        }
        _manager.applyChanges(reversed);
        //ChangeApplied ca = _manager.applyChanges(reversed);
        //LOGGER.info("revertChange "+ca+" for "+changes.toString());
        // synchronize reasoner
        if( _reasoner != null ) _reasoner.flush();
    }

    public void teardown() {
        if( _reasoner != null )
            _reasoner.dispose();
    }
}