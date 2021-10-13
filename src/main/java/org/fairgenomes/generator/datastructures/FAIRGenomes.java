package org.fairgenomes.generator.datastructures;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FAIRGenomes {

    /*
    Variables mapped to the YAML file
     */
    public String name;
    public String description;
    public Double version;
    public ReleaseType releaseType;
    public LocalDate date;
    public File lookupGlobalOptions;
    public List<Author> authors;
    public Copyright copyright;
    public License license;
    public List<Module> modules;

    /*
    Variables loaded afterwards
     */
    public LookupList lookupGlobalOptionsInstance;
    public int totalNrOfLookupsWithoutGlobals;
    public Map<String, Module> moduleMap;


    /**
     * Load the lookupGlobalOptions
     * @throws Exception
     */
    public void loadLookupGlobalOptions() throws Exception {
        LookupList ll = new LookupList(lookupGlobalOptions);
        lookupGlobalOptionsInstance = ll;
    }


    /**
     * Parse the 'values' information to ValueType enum values
     * @throws Exception
     */
    public void parseElementValueTypes() throws Exception {
        for(Module m: modules)
        {
            for(Element e : m.elements)
            {
                int whiteSpaceIndex = e.values.indexOf(" ");
                String vt = whiteSpaceIndex > 0 ? e.values.substring(0, whiteSpaceIndex) : e.values;
                ValueType valueType = ValueType.valueOf(vt);
                e.valueTypeEnum = valueType;
            }
        }
    }

    /**
     * Load the lookups for each element
     * @throws Exception
     */
    public void loadElementLookups() throws Exception {
        totalNrOfLookupsWithoutGlobals = 0;
        moduleMap = new HashMap<String, Module>();
        for(Module m: modules)
        {
            moduleMap.put(m.technicalName, m);
            m.elementMap = new HashMap<String, Element>();
            for(Element e : m.elements)
            {
                m.elementMap.put(e.technicalName, e);
                if(e.isLookup())
                {
                    int whiteSpaceIndex = e.values.indexOf(" ");
                    int commaIndex = e.values.indexOf(",");
                    String vt = whiteSpaceIndex > 0 ? e.values.substring(whiteSpaceIndex, commaIndex > 0 ? commaIndex : e.values.length()) : e.values;
                    e.type = commaIndex > 0 ? e.values.substring(commaIndex).replace(", ofType [", "").replace("]", "").trim() : null;
                    vt = vt.replace("[", "").replace("]", "").trim();
                    LookupList ll = new LookupList(new File(vt));
                    e.lookup = ll;
                    e.nrOfLookupsWithoutGlobals = ll.lookups.size();
                    totalNrOfLookupsWithoutGlobals += ll.lookups.size();

                    /*
                    Add the global lookups unless NoGlobals is specified
                    */
                    if(!(e.valueTypeEnum.equals(ValueType.LookupOne_NoGlobals) || e.valueTypeEnum.equals(ValueType.LookupMany_NoGlobals))) {
                        e.lookup.lookups.putAll(lookupGlobalOptionsInstance.lookups);
                    }
                }
                else if(e.isReference())
                {
                    boolean found = false;
                    for(Module mm : modules)
                    {
                        if(mm.name.equals(e.referenceTo))
                        {
                            // instances of reference fields (i.e. foreign keys) are typed as the module IRI they refer to
                            // this is not part of any output formats, since these references do not exist yet!
                            // this instance type is different from the field type, i.e.
                            // 'Belongs to sequencing' has type NCIT_C25683 (Source), while instances refer to the
                            // Sequencing module, and are therefore types as EDAM:topic_3168 (Sequencing)
                            e.type = m.parsedOntology.iri;
                            found = true;
                            break;
                        }
                    }
                    if(!found)
                    {
                        throw new Exception("Unable to find module reference '"+e.referenceTo+"' for setting value type");
                    }
                }
            }
        }
    }

    /**
     * Parse and split ontology info to code, codesystem and iri
     * @throws Exception
     */
    public void parseOntologies() throws Exception {
        for (Module m : modules) {
            int whiteSpaceIndex = m.ontology.indexOf(" ");
            String[] split = parseOntoInfo(whiteSpaceIndex, m.ontology);
            m.parsedOntology = new Ontology();
            m.parsedOntology.codeSystem = split[0];
            m.parsedOntology.code = split[1];
            m.parsedOntology.iri = m.ontology.substring(whiteSpaceIndex).replace("[", "").replace("]", "").trim();
            for (Element e : m.elements) {
                whiteSpaceIndex = e.ontology.indexOf(" ");
                split = parseOntoInfo(whiteSpaceIndex, e.ontology);
                e.parsedOntology = new Ontology();
                e.parsedOntology.codeSystem = split[0];
                e.parsedOntology.code = split[1];
                e.parsedOntology.iri = e.ontology.substring(whiteSpaceIndex).replace("[", "").replace("]", "").trim();
            }
        }
    }

    /**
     * Parse and split SKOS matches for mapping
     * @throws Exception
     */
    public void parseMatches() throws Exception {
        for(Module m: modules) {
            for (Element e : m.elements) {
                if(e.exactMatch != null) { addMatches(Match.exactMatch, e, e.exactMatch); }
                if(e.closeMatch != null) { addMatches(Match.closeMatch, e, e.closeMatch); }
                if(e.relatedMatch != null) { addMatches(Match.relatedMatch, e, e.relatedMatch); }
                if(e.broadMatch != null) { addMatches(Match.broadMatch, e, e.broadMatch); }
                if(e.narrowMatch != null) { addMatches(Match.narrowMatch, e, e.narrowMatch); }

                /**
                 * for debug
                 */
//                if(e.exactMatch != null || e.closeMatch != null || e.relatedMatch != null || e.broadMatch != null || e.narrowMatch != null)
//                {
//                    for(Match key : e.matches.keySet())
//                    {
//                        for(Ontology o : e.matches.get(key))
//                        {
//                            System.out.println(key + " -> " + o);
//                        }
//                    }
//                }

            }
        }
    }

    private void addMatches(Match m, Element e, String value) throws Exception {
        if(e.matches == null)
        {
            e.matches = new HashMap<Match, List<Ontology>>();
        }
        if(!e.matches.containsKey(m))
        {
            e.matches.put(m, new ArrayList<Ontology>());
        }
        String[] matchSplit = value.split(",", -1);
        for(String match : matchSplit)
        {
            match = match.trim();
            int whiteSpaceIndex = match.indexOf(" ");
            String[] split = parseOntoInfo(whiteSpaceIndex, match);
            Ontology o = new Ontology();
            o.codeSystem = split[0];
            o.code = split[1];
            o.iri = match.substring(whiteSpaceIndex).replace("[", "").replace("]", "").trim();
            e.matches.get(m).add(o);
        }
    }

    private String[] parseOntoInfo(int whiteSpaceIndex, String ontoInfo) throws Exception {
        if(whiteSpaceIndex == -1)
        {
            throw new Exception("bad ontology info: " + ontoInfo + ", no whitespace");
        }
        String codeAndCodeSystem = ontoInfo.substring(0, whiteSpaceIndex);
        if(!codeAndCodeSystem.contains(":")){
            throw new Exception("bad ontology info: " + ontoInfo + ", no colon");
        }
        return codeAndCodeSystem.split(":", -1);
    }

    /**
     * Wrapper to create technical names
     * @return
     */
    public void createElementTechnicalNames() throws Exception {
        for (Module m : modules) {
            m.technicalName = toTechName(m.name);
            for (Element e : m.elements) {
                e.technicalName = toTechName(e.name);
            }
        }
    }

    /**
     * Parse any references to other modules or lookups
     * @throws Exception
     */
    public void parseReferences() throws Exception {
        for (Module m : modules) {
            for (Element e : m.elements) {
                if(e.isReference())
                {
                    int whiteSpaceIndex = e.values.indexOf(" ");
                    e.referenceTo = e.values.substring(whiteSpaceIndex).replace("[", "").replace("]", "").trim();
                }
            }
        }
    }

    /**
     * Add module pointers to the elements
     * @throws Exception
     */
    public void setElementModules() throws Exception {
        for (Module m : modules) {
            for (Element e : m.elements) {
                e.m = m;
            }
        }
    }

    /**
     * Helper to simplify names for technical use
     * @param in
     * @return
     */
    public static String toTechName(String in)
    {
        return in.replace(" ", "").toLowerCase();
    }

    @Override
    public String toString() {
        return "FAIRGenomes{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", version=" + version +
                ", date=" + date +
                ", lookupGlobalOptions=" + lookupGlobalOptions +
                ", modules=" + modules +
                ", lookupGlobalOptionsInstance=" + lookupGlobalOptionsInstance +
                '}';
    }
}