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

package com.hpl.mds.test.valid;

import com.hpl.mds.annotations.Managed;
import com.hpl.mds.annotations.RecordSchema;
import com.hpl.mds.prim.ManagedBoolean;
import com.hpl.mds.prim.ManagedByte;
import com.hpl.mds.prim.ManagedDouble;
import com.hpl.mds.prim.ManagedFloat;
import com.hpl.mds.prim.ManagedInt;
import com.hpl.mds.prim.ManagedLong;
import com.hpl.mds.prim.ManagedShort;
import com.hpl.mds.string.ManagedString;

@RecordSchema
public interface MethodsManagedManagedSchema {
    
    static void intParamMethod(MethodsManagedManaged.Private self, @Managed ManagedInt val){}
    
    static void shortParamMethod(MethodsManagedManaged.Private self, @Managed ManagedShort val){}
    
    static void longParamMethod(MethodsManagedManaged.Private self, @Managed ManagedLong val){}
    
    static void floatParamMethod(MethodsManagedManaged.Private self, @Managed ManagedFloat val){}
    
    static void doubleParamMethod(MethodsManagedManaged.Private self, @Managed ManagedDouble val){}
    
    static void booleanParamMethod(MethodsManagedManaged.Private self, @Managed ManagedBoolean val){}
    
    static void byteParamMethod(MethodsManagedManaged.Private self, @Managed ManagedByte val){}
    
    static void stringParamMethod(MethodsManagedManaged.Private self, @Managed ManagedString val){}

}
