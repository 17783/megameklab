/*
 * MegaMekLab - Copyright (C) 2017 - The MegaMek Team
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 */
package megameklab.com.printing;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import java.io.File;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.Locale;
import java.util.StringJoiner;

import org.apache.batik.anim.dom.SVGGraphicsElement;
import org.apache.batik.anim.dom.SVGLocatableSupport;
import org.apache.batik.util.SVGConstants;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.svg.SVGRect;
import org.w3c.dom.svg.SVGRectElement;
import org.w3c.dom.svg.SVGTextContentElement;

import megamek.common.CrewType;
import megamek.common.Entity;
import megamek.common.EquipmentType;
import megamek.common.Mech;
import megamek.common.UnitRole;
import megamek.common.UnitRoleHandler;
import megamek.common.options.IOption;
import megamek.common.options.IOptionGroup;
import megamek.common.options.PilotOptions;
import megamek.common.options.Quirks;

/**
 * Base class for printing Entity record sheets
 * 
 * @author Neoancient
 *
 */
public abstract class PrintEntity extends PrintRecordSheet {
    
    // Armor types with special properties that should be identified on the record sheet
    private static final long AT_SPECIAL = (1 << EquipmentType.T_ARMOR_REACTIVE)
            | (1 << EquipmentType.T_ARMOR_REFLECTIVE)
            | (1 << EquipmentType.T_ARMOR_HARDENED)
            | (1 << EquipmentType.T_ARMOR_STEALTH)
            | (1 << EquipmentType.T_ARMOR_FERRO_LAMELLOR)
            | (1 << EquipmentType.T_ARMOR_STEALTH_VEHICLE)
            | (1 << EquipmentType.T_ARMOR_ANTI_PENETRATIVE_ABLATION)
            | (1 << EquipmentType.T_ARMOR_HEAT_DISSIPATING)
            | (1 << EquipmentType.T_ARMOR_IMPACT_RESISTANT)
            | (1 << EquipmentType.T_ARMOR_BALLISTIC_REINFORCED);
    
    /**
     * Creates an SVG object for the record sheet
     * 
     * @param startPage The print job page number for this sheet
     * @param options Overrides the global options for which elements are printed 
     */
    protected PrintEntity(int startPage, RecordSheetOptions options) {
        super(startPage, options);
    }

    protected abstract Entity getEntity();
    
    /**
     * When printing from a MUL the pilot data is filled in unless the option has been disabled. This
     * allows a series of blank record sheets to be generated without including the generated pilot data.
     * If the crew name is "unnamed" then we are printing directly from MML or file/cache and the
     * pilot data should not be filled in.
     * 
     * @return Whether the pilot data should be filled in.
     */
    protected boolean showPilotInfo() {
        return options.showPilotData() && !getEntity().getCrew().getName().equalsIgnoreCase("unnamed");
    }
    
    /**
     * Builds the string to display for the quirks block. Returns an empty string if quirks are
     * disabled (or if the unit has no quirks).
     * 
     * @return The text to display for the unit's quirks.
     */
    protected String formatQuirks() {
        if (options.showQuirks()) {
            StringJoiner sj = new StringJoiner(", ");
            Quirks quirks = getEntity().getQuirks();
            for (Enumeration<IOptionGroup> optionGroups = quirks.getGroups(); optionGroups.hasMoreElements();) {
                IOptionGroup optiongroup = optionGroups.nextElement();
                if (quirks.count(optiongroup.getKey()) > 0) {
                    for (Enumeration<IOption> options = optiongroup.getOptions(); options.hasMoreElements();) {
                        IOption option = options.nextElement();
                        if (option != null && option.booleanValue()) {
                            sj.add(option.getDisplayableNameWithValue());
                        }
                    }
                }
            }
            return sj.toString();
        } else {
            return "";
        }
    }
    
    @Override
    protected void printImage(Graphics2D g2d, PageFormat pageFormat, int pageNum) {
        Element element = null;
        
        element = getSVGDocument().getElementById("tspanCopyright");
        if (null != element) {
            element.setTextContent(String.format(element.getTextContent(),
                    Calendar.getInstance().get(Calendar.YEAR)));
        }
        
        writeTextFields();
        drawArmor();
        drawStructure();
        Element eqRect = getSVGDocument().getElementById("inventory");
        if (eqRect instanceof SVGRectElement) {
            writeEquipment((SVGRectElement) eqRect);
        }
        if (options.showEraIcon()) {
            drawEraIcon();
        }
        drawFluffImage();
    }
    
    protected void writeTextFields() {
        setTextField("title", getRecordSheetTitle().toUpperCase());
        setTextField("type", getEntity().getShortNameRaw());
        setTextField("mpWalk", formatWalk());
        setTextField("mpRun", formatRun());
        setTextField("mpJump", formatJump());
        setTextField("tonnage", NumberFormat.getInstance().format((int) getEntity().getWeight()));
        setTextField("techBase", formatTechBase());
        setTextField("rulesLevel", formatRulesLevel());
        setTextField("era", formatEra(getEntity().getYear()));
        setTextField("cost", formatCost());
        // If we're using a MUL to print generic sheets we also want to ignore any BV adjustments
        // for C3 networks or pilot skills.
        setTextField("bv", NumberFormat.getInstance().format(getEntity()
                .calculateBattleValue(!showPilotInfo(), !showPilotInfo())));
        UnitRole role = UnitRoleHandler.getRoleFor(getEntity());
        if (!options.showRole() || (role == UnitRole.UNDETERMINED)) {
            hideElement("lblRole", true);
            hideElement("role", true);
        } else {
            setTextField("role", role.toString());
        }
        
        // If we need to fill in names of crew slots we will need to reposition blanks/name fields.
        // This will require building the graphics tree so we measure the elements.
        if (getEntity().getCrew().getCrewType() != CrewType.SINGLE) {
            build();
        }
        for (int i = 0; i < getEntity().getCrew().getSlotCount(); i++) {
            // If we have multiple named crew for the unit, change the "Name:" label to
            // the label of the slot. This will usually require adjusting the position of the
            // name or the length of the blank.
            double nameOffset = 0;
            if (getEntity().getCrew().getSlotCount() > 1) {
                Element element = getSVGDocument().getElementById("crewName" + i);
                if (null != element) {
                    float oldWidth = ((SVGTextContentElement) element).getComputedTextLength();
                    element.setTextContent(getEntity().getCrew().getCrewType().getRoleName(i) + ":");
                    nameOffset = SVGLocatableSupport.getBBox(element).getWidth() - oldWidth;
                }
            }
            if (showPilotInfo()) {
                Element element = getSVGDocument().getElementById("blanksCrew" + i);
                if (null != element) {
                    hideElement(element);
                }
                if (nameOffset != 0) {
                    element = getSVGDocument().getElementById("pilotName" + i);
                    if (null != element) {
                        double offset = nameOffset;
                        String prev = element.getAttribute(SVGConstants.SVG_X_ATTRIBUTE);
                        if (null != prev) {
                            offset += Double.parseDouble(prev);
                        } else {
                            offset += ((SVGTextContentElement) element).getStartPositionOfChar(0).getX();
                        }
                        element.setAttributeNS(null, SVGConstants.SVG_X_ATTRIBUTE, Double.toString(offset));
                    }
                }
                setTextField("pilotName" + i, getEntity().getCrew().getName(i), true);
                setTextField("gunnerySkill" + i, Integer.toString(getEntity().getCrew().getGunnery(i)), true);
                setTextField("pilotingSkill" + i, Integer.toString(getEntity().getCrew().getPiloting(i)), true);
                
                StringJoiner spaList = new StringJoiner(", ");
                PilotOptions spas = getEntity().getCrew().getOptions();
                for (Enumeration<IOptionGroup> optionGroups = spas.getGroups(); optionGroups.hasMoreElements();) {
                    IOptionGroup optiongroup = optionGroups.nextElement();
                    if (spas.count(optiongroup.getKey()) > 0) {
                        for (Enumeration<IOption> options = optiongroup.getOptions(); options.hasMoreElements();) {
                            IOption option = options.nextElement();
                            if (option != null && option.booleanValue()) {
                                spaList.add(option.getDisplayableNameWithValue().replaceAll(" \\(.*?\\)", ""));
                            }
                        }
                    }
                }
                if (spaList.length() > 0) {
                    Element rect = getSVGDocument().getElementById("spas" + (getEntity().getCrew().getSlotCount() - 1));
                    if (rect instanceof SVGRectElement) {
                        Rectangle2D bbox = getRectBBox((SVGRectElement) rect);
                        Element canvas = (Element) ((Node) rect).getParentNode();
                        String spaText = "Abilities: " + spaList.toString();
                        float fontSize = FONT_SIZE_MEDIUM;
                        if (getTextLength(spaText, fontSize) > bbox.getWidth()) {
                            fontSize = (float) bbox.getHeight() / 2.4f;
                        }
                        double lineHeight = fontSize * 1.2;
                        addMultilineTextElement(canvas, bbox.getX(), bbox.getY() + lineHeight,
                                bbox.getWidth(), lineHeight, spaText, fontSize, "start", "normal",
                                "black", ' ');
                    }
                }
    
            } else {
                setTextField("pilotName" + i, null);
                setTextField("gunnerySkill" + i, null);
                setTextField("pilotingSkill" + i, null);
                if (nameOffset != 0) {
                    Element element = getSVGDocument().getElementById("blankCrewName" + i);
                    if (null != element) {
                        SVGRect rect = ((SVGGraphicsElement) element).getBBox();
                        element.setAttributeNS(null, SVGConstants.SVG_D_ATTRIBUTE,
                                String.format("M %f,%f %f,%f", rect.getX() + nameOffset, rect.getY(),
                                        rect.getX() + rect.getWidth(), rect.getY()));
                    }
                }
            }
        }
    }
    
    protected void drawArmor() {
        if (!getEntity().hasPatchworkArmor()) {
            if ((AT_SPECIAL & (1 << getEntity().getArmorType(1))) != 0) {
                String[] atName = EquipmentType.getArmorTypeName(getEntity().getArmorType(1)).split("\\-");
                setTextField("armorType", atName[0]);
                if (atName.length > 1) {
                    setTextField("armorType2", atName[1]);
                }
            } else {
                hideElement("armorType", true);
            }
        } else {
            boolean hasSpecial = false;
            for (int loc = firstArmorLocation(); loc < getEntity().locations(); loc++) {
                if (((AT_SPECIAL & (1 << getEntity().getArmorType(loc))) != 0)
                        // Stealth armor loses special properties when used with patchwork, so we don't
                        // need to show it.
                        && (getEntity().getArmorType(loc) != EquipmentType.T_ARMOR_STEALTH)
                        && (getEntity().getArmorType(loc) != EquipmentType.T_ARMOR_STEALTH_VEHICLE)) {
                    String atName = EquipmentType.getArmorTypeName(getEntity().getArmorType(loc));
                    String eleName = "patchwork" + getEntity().getLocationAbbr(loc);
                    int index = atName.indexOf('-');
                    if ((index < 0) || (getSVGDocument().getElementById(eleName + "2") == null)) {
                        setTextField("patchwork" + getEntity().getLocationAbbr(loc), atName);
                    } else {
                        setTextField("patchwork" + getEntity().getLocationAbbr(loc),
                                atName.substring(0, index));
                        setTextField("patchwork" + getEntity().getLocationAbbr(loc) + "2",
                                atName.substring(index + 1));
                    }
                    hasSpecial = true;
                }
            }
            if (hasSpecial) {
                setTextField("armorType", EquipmentType.getArmorTypeName(EquipmentType.T_ARMOR_PATCHWORK));
            } else {
                hideElement("armorType", true);
            }
        }
        final String FORMAT = "( %d )";
        for (int loc = firstArmorLocation(); loc < getEntity().locations(); loc++) {
            setTextField("textArmor_" + getEntity().getLocationAbbr(loc),
                    String.format(FORMAT, getEntity().getOArmor(loc)));
            setTextField("textIS_" + getEntity().getLocationAbbr(loc),
                    String.format(FORMAT, getEntity().getOInternal(loc)));
        }
        drawArmorStructurePips();
    }

    /**
     * Add armor and structure pips for each location.
     */
    protected void drawArmorStructurePips() {
        Element element = null;
        for (int loc = firstArmorLocation(); loc < getEntity().locations(); loc++) {
            if ((getEntity() instanceof Mech) && ((Mech) getEntity()).isSuperHeavy() && (loc == Mech.LOC_HEAD)) {
                element = getSVGDocument().getElementById("armorPips" + getEntity().getLocationAbbr(loc) + "_SH");
            } else {
                element = getSVGDocument().getElementById("armorPips" + getEntity().getLocationAbbr(loc));
            }
            if (null != element) {
                addPips(element, getEntity().getOArmor(loc), isCenterlineLocation(loc),
                        PipType.forAT(getEntity().getArmorType(loc)));
            }
        }
    }
    
    /**
     * Identifies the index of the first location that can be armored. For vehicles this should be 1
     * to skip the body.
     * 
     * @return The lowest location index that can be armored.
     */
    protected int firstArmorLocation() {
        return 0;
    }
    
    /**
     * Identifies which locations are on the unit's centerline and should have armor and structure
     * pips laid out with left-right symmetry
     * 
     * @param loc The location to check
     * @return    Whether the location is along the unit's centerline
     */
    protected abstract boolean isCenterlineLocation(int loc);
    
    protected void drawStructure() {
        
    }
    
    protected void writeEquipment(SVGRectElement canvas) {
        
    }
    
    protected void drawFluffImage() {
        
    }
    
    private void drawEraIcon() {
        File iconFile = null;
        if (getEntity().getYear() < 2781) {
            iconFile = new File("data/images/recordsheets/era_starleague.png");
        } else if (getEntity().getYear() < 3050) {
            iconFile = new File("data/images/recordsheets/era_sw.png");
        } else if (getEntity().getYear() < 3061) {
            iconFile = new File("data/images/recordsheets/era_claninvasion.png");
        } else if (getEntity().getYear() < 3068) {
            iconFile = new File("data/images/recordsheets/era_civilwar.png");
        } else if (getEntity().getYear() < 3086) {
            iconFile = new File("data/images/recordsheets/era_jihad.png");
        } else {
            iconFile = new File("data/images/recordsheets/era_darkage.png");
        }
        Element rect = getSVGDocument().getElementById("eraIcon");
        if (rect instanceof SVGRectElement) {
            embedImage(iconFile,
                    (Element) ((Node) rect).getParentNode(), getRectBBox((SVGRectElement) rect), true);
        }
    }
    
    protected String formatWalk() {
        return Integer.toString(getEntity().getWalkMP());
    }
    
    protected String formatRun() {
        return Integer.toString(getEntity().getRunMP());
    }
    
    protected String formatJump() {
        return Integer.toString(getEntity().getJumpMP());
    }

    protected String formatTechBase() {
        if (getEntity().isMixedTech()) {
            return "Mixed";
        } else if (getEntity().isClan()) {
            return "Clan";
        } else {
            return "Inner Sphere";
        }
    }
    
    protected String formatRulesLevel() {
        return getEntity().getStaticTechLevel().toString().substring(0, 1)
                + getEntity().getStaticTechLevel().toString().substring(1).toLowerCase();
    }
    
    private static String formatEra(int year) {
        if (year < 2571) {
            return "Age of War";
        } else if (year < 2781) {
            return "Star League";
        } else if (year < 2901) {
            return "Early Succession War";
        } else if (year < 3050) {
            return "Late Succession War";
        } else if (year < 3062) {
            return "Clan Invasion";
        } else if (year < 3068) {
            return "Civil War";
        } else if (year < 3086) {
            return "Jihad";
        } else if (year < 3101) {
            return "Early Republic";
        } else if (year < 3131) {
            return "Late Republic";
        } else {
            return "Dark Ages";
        }
    }
    
    protected String formatCost() {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.getDefault());
        return nf.format(getEntity().getCost(true)) + " C-bills";
    }
    
}
