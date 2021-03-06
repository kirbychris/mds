/*
 *
 *  Managed Data Structures
 *  Copyright © 2016 Hewlett Packard Enterprise Development Company LP.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  As an exception, the copyright holders of this Library grant you permission
 *  to (i) compile an Application with the Library, and (ii) distribute the 
 *  Application containing code generated by the Library and added to the 
 *  Application during this compilation process under terms of your choice, 
 *  provided you also meet the terms and conditions of the Application license.
 *
 */

package com.hpl.erk.types.templates;

import java.util.Map;

import com.hpl.erk.formatters.SeqFormatter;
import com.hpl.erk.types.TypeBound;

public class TemplateRange implements TypeTemplate {
  public final TypeTemplate[] upperBounds;
  public final TypeTemplate[] lowerBounds;
  
  public TemplateRange(TypeTemplate[] lowerBounds, TypeTemplate[] upperBounds) {
    this.upperBounds = upperBounds;
    this.lowerBounds = lowerBounds;
  }

  @Override
  public String toString() {
    String ldesc = lowerBounds.length==0 ? "" : " super "+SeqFormatter.withSep(" & ").format(lowerBounds);
    String udesc = upperBounds.length==0 ? "" : " extends "+SeqFormatter.withSep(" & ").format(upperBounds);
    return "?"+ldesc+udesc;
  }
  
  @Override
  public void fillAncestors(Map<Class<?>, TypeTemplate> map) {
    throw new UnsupportedOperationException(String.format("Template range %s cannot be ancestor", this));
  }

  @Override
  public void inferBounds(TypeBound token, TypeBound[] bounds) {
    // TODO Auto-generated method stub
    
  }

}
