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

package com.hpl.erk.config.type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.hpl.erk.ReadableString;
import com.hpl.erk.config.DelayedVal;
import com.hpl.erk.config.GenericCache;
import com.hpl.erk.config.GenericType;
import com.hpl.erk.config.PType;
import com.hpl.erk.config.ReadDelayedVal;
import com.hpl.erk.config.ex.IllegalValueException;
import com.hpl.erk.config.ex.ReadError;
import com.hpl.erk.func.Functions;
import com.hpl.erk.func.Pair;
import com.hpl.erk.types.GenericTypeToken;
import com.hpl.erk.types.TypeToken;

public class MappingType<KT, VT, MT extends Map<KT,VT>> extends TwoArgCompositeType<MT, KT, VT> implements MappishType<MT,KT,VT>{
  private final Generic<? super MT, ? super KT, ? super VT> mgeneric;
  
  
  @SuppressWarnings("rawtypes")
  public static class Generic<GT extends Map, B1, B2> extends TwoArgCompositeType.Generic<GT, B1, B2> {
    private CollectionFactory factory = null;
    
    public abstract class CollectionFactory {
      protected CollectionFactory() {
        factory = this;
      }
      public abstract <KT extends B1, VT extends B2> GT makeEmpty(PType<KT> ktype, PType<VT> vtype);
    }
    
    protected Generic(GenericTypeToken generic) {
      super(generic);
    }
    
    public final <KT extends B1, VT extends B2, T extends GT> PType<T> of(Sig<T> sig, final PType<KT> keyType, final PType<VT> valType) {
      return MappingType.of(sig, this, keyType, valType);  
    }
    
    public <CT extends GT, KT extends B1, VT extends B2> CT makeEmpty(PType<CT> ctype, PType<KT> ktype, PType<VT> vtype) {
      if (factory == null) {
        throw new IllegalStateException(String.format("%s doesn't know how to make a %s", this, ctype));
      }
      @SuppressWarnings("unchecked")
      CT val = (CT)factory.makeEmpty(ktype, vtype);
      return val;
    }
    
    public void setFactory(CollectionFactory factory) {
      this.factory = factory;
    }
    
    @Override
    public <T extends GT, P1 extends B1, P2 extends B2> PType<T> makeNewType(PType.Sig<T> sig, TypeToken token, PType<P1> p1Type, PType<P2> p2Type) {
      return new MappingType<>(this, token, p1Type, p2Type);
    }
    
    
  }
  
//  public static interface CollectionFactory<GT extends Collection, B1> {
//    public <CT extends GT, ET extends B1> CT makeEmpty(Type<CT> ctype, Type<ET> etype);
//  }

  protected MappingType(GenericType<? super MT> generic, TypeToken typeToken, PType<KT> keyType, PType<VT> valType) {
    super(generic, typeToken, keyType, valType);
    if (!(generic instanceof Generic)) {
      throw new IllegalArgumentException(String.format("%s is not a Collection Generic for %s", generic, typeToken));
    }
    @SuppressWarnings("unchecked")
    Generic<? super MT, ? super KT, ? super VT> downcast = (Generic<? super MT, ? super KT, ? super VT>)generic;
    mgeneric = downcast;
  }

  public MT createEmpty() {
    return mgeneric.makeEmpty(this, p1Type, p2Type);
  }
  
  

  /**
   * @throws ReadError  
   * @throws ReadDelayedVal 
   */
  protected MT readSpecialForms(ReadableString input, int resetTo) throws ReadError, ReadDelayedVal {
    return null;
  }

  
  static interface TerminationCriteria {
    boolean done(ReadableString input);
  }

  
  @Override
  public MT readVal(ReadableString input, final String valTerminators) throws ReadError, ReadDelayedVal {
    int resetTo = input.getCursor();
    MT collection = readSpecialForms(input, resetTo);
    if (collection != null) {
      return collection;
    }
    MT map = createEmpty();
    if (input.consume("{")) {
      return readSequence(input, resetTo, map, new TerminationCriteria() {
        @Override
        public boolean done(ReadableString input) {
          return input.consume("}");
        }
      });
    }
    if (!valTerminators.contains(",")) {
      return readSequence(input, resetTo, map, new TerminationCriteria() {
        @Override
        public boolean done(ReadableString input) {
          return input.atEnd() || input.atAny(valTerminators);
        }
      });
    }
    throw new ReadError(input, resetTo, this, "Not a map");
  }
  
  private MT readSequence(ReadableString input, int resetTo, final MT map, TerminationCriteria term) throws ReadError, ReadDelayedVal {
    input.skipWS();
    
    boolean first = true;
    List<DPV<KT,VT>> delayedPairs = null;
    Map<String,String> delayDesc = null;
    while (!term.done(input)) {
      if (!first) {
        if (!input.consume(",")) {
          throw new ReadError(input, resetTo, this, "Expected a comma");
        }
      }
      input.skipWS();
      KT key;
      DelayedVal<? extends KT> delayedKey = null;
      try {
        key = p1Type.read(input, "=");
      } catch (ReadDelayedVal e) {
        delayedKey = e.<KT>delayedVal();
        key = null;
      }
      input.consume("=");
      DelayedVal<? extends VT> delayedVal = null;
      VT val;
      try {
        val = p2Type.read(input, ",}");
      } catch (ReadDelayedVal e) {
        delayedVal = e.<VT>delayedVal();
        val = null;
      }
      first = false;
      input.skipWS();
      DPV<KT, VT> delayedPair = checkDelays(key, delayedKey, val, delayedVal);
      if (delayedPair != null) {
        if (delayedPairs == null) {
          delayedPairs = new ArrayList<>();
        }
        if (delayDesc == null) {
          delayDesc = new HashMap<>();
        }
        delayedPairs.add(delayedPair);
        delayDesc.put(delayedPair.kDesc, delayedPair.vDesc);
      } else {
        map.put(key, val);
      }
    }
    if (delayedPairs == null) {
      return map;
    }
    for (Entry<KT, VT> entry : map.entrySet()) {
      assert delayDesc != null;
      delayDesc.put(p1Type.format(entry.getKey()), p2Type.format(entry.getValue()));
    }
    final List<DPV<KT, VT>> finalDelayedPairs = delayedPairs;
    throw new ReadDelayedVal(new DelayedVal<MT>(String.format("%s", delayDesc)) {
      @Override
      public MT force() throws IllegalValueException {
        for (DPV<KT, VT> dp : finalDelayedPairs) {
          Pair<KT, VT> pair = dp.force();
          map.put(pair.key, pair.value);
        }
        return map;
      }});
  }

  private static abstract class DPV<KT,VT> extends DelayedVal<Pair<KT,VT>> {
    final String kDesc;
    final String vDesc;
    protected DPV(String kDesc, String vDesc) {
      super(kDesc+"="+vDesc);
      this.kDesc = kDesc;
      this.vDesc = vDesc;
    }
    
  }

  private DPV<KT,VT> checkDelays(
      final KT key, final DelayedVal<? extends KT> delayedKey, 
      final VT val, final DelayedVal<? extends VT> delayedVal) {
    if (delayedKey != null) {
      if (delayedVal != null) {
        return new DPV<KT,VT>(delayedKey.desc, delayedVal.desc) {
          @Override
          public Pair<KT, VT> force() throws IllegalValueException {
            return Functions.<KT,VT>pair(delayedKey.force(), delayedVal.force());
          }
        };
      } else {
        return new DPV<KT,VT>(delayedKey.desc, p2Type.format(val)) {
          @Override
          public Pair<KT, VT> force() throws IllegalValueException {
            return Functions.<KT,VT>pair(delayedKey.force(), val);
          }
        };
      }
    } else if (delayedVal != null) {
      return new DPV<KT,VT>(p1Type.format(key), delayedVal.desc) {
        @Override
        public Pair<KT, VT> force() throws IllegalValueException {
          return Functions.<KT,VT>pair(key, delayedVal.force());
        }
      };
    } else {
      return null;
    }
  }

  @SuppressWarnings("rawtypes")
  public static <GT extends Map, B1, B2> Generic<GT,B1,B2> generic(final GenericTypeToken gtoken, final PType<B1> kBound, final PType<B2> vBound) {
    GenericCache.Factory<GT> factory = new GenericCache.Factory<GT>() {
      @Override
      public Generic<GT, B1,B2> create() {
        return new Generic<GT,B1,B2>(gtoken);
      }
    };
    return (Generic<GT, B1, B2>)GenericType.cache.lookup(gtoken, factory);  
  }

  @Override
  public PType<KT> keyType() {
    return p1Type;
  }

  @Override
  public PType<VT> valType() {
    return p2Type;
  }

  @Override
  public PType<MT> asType() {
    return this;
  }
  
  public Map<KT,VT> asMap(MT val) {
    return val;
  };
  
  @Override
  public MT copyFrom(Map<? extends KT, ? extends VT> map) {
    MT newMap = createEmpty();
    newMap.putAll(map);
    return newMap;
  }

}
