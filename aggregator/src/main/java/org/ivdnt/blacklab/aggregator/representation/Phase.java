package org.ivdnt.blacklab.aggregator.representation;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import nl.inl.anw.ArtikelFase;

@XmlAccessorType(XmlAccessType.FIELD)
@SuppressWarnings("unused")
@XmlRootElement(name="phase")
public class Phase {
    
    private int id;
    
    private String name;
    
    private String nameInList;
    
    public Phase(ArtikelFase f) {
        super();
        this.id = f.getId();
        this.name = f.getNaam();
        this.nameInList = f.getNaamInLijst();
    }
    
    // Required for Jersey
    private Phase() {}
    
}
