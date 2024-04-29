/*
 * BSD 3-Clause License
 * 
 * Copyright (c) 2024, Bram Stout Productions
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package nl.bramstout.mcworldexporter.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.JsonElement;

import nl.bramstout.mcworldexporter.nbt.NBT_Tag;
import nl.bramstout.mcworldexporter.nbt.TAG_Byte;
import nl.bramstout.mcworldexporter.nbt.TAG_Compound;
import nl.bramstout.mcworldexporter.nbt.TAG_Double;
import nl.bramstout.mcworldexporter.nbt.TAG_Float;
import nl.bramstout.mcworldexporter.nbt.TAG_Int;
import nl.bramstout.mcworldexporter.nbt.TAG_Long;
import nl.bramstout.mcworldexporter.nbt.TAG_Short;
import nl.bramstout.mcworldexporter.nbt.TAG_String;

public class BlockStateVariant extends BlockStatePart{
	
	private List<Map<String, String>> checks;
	private boolean _needsConnectionInfo;
	
	public BlockStateVariant(String checkString, JsonElement data, boolean doubleSided) {
		super();
		
		_needsConnectionInfo = false;
		checks = new ArrayList<Map<String, String>>();
		for(String checkToken : checkString.split("\\|\\|")) {
			Map<String, String> check = new HashMap<String, String>();
			for(String checkToken2 : checkToken.split(",")) {
				if(checkToken2.contains("=")) {
					String[] tokens = checkToken2.split("=");
					if(tokens[0].startsWith("miex_connect"))
						_needsConnectionInfo = true;
					check.put(tokens[0], tokens[1]);
				}
			}
			checks.add(check);
		}
		
		if(data.isJsonArray()) {
			for(JsonElement el : data.getAsJsonArray().asList()) {
				int modelId = ModelRegistry.getIdForName(el.getAsJsonObject().get("model").getAsString(), doubleSided);
				int rotX = 0;
				int rotY = 0;
				boolean uvLock = false;
				if(el.getAsJsonObject().has("x"))
					rotX = el.getAsJsonObject().get("x").getAsInt();
				if(el.getAsJsonObject().has("y"))
					rotY = el.getAsJsonObject().get("y").getAsInt();
				if(el.getAsJsonObject().has("uvlock"))
					uvLock = el.getAsJsonObject().get("uvlock").getAsBoolean();
				Model model = new Model(ModelRegistry.getModel(modelId));
				if(rotX != 0 || rotY != 0)
					model.rotate(rotX, rotY, uvLock);
				if(el.getAsJsonObject().has("weight"))
					model.setWeight(el.getAsJsonObject().get("weight").getAsInt());
				models.add(model);
			}
		} else if(data.isJsonObject()) {
			int modelId = ModelRegistry.getIdForName(data.getAsJsonObject().get("model").getAsString(), doubleSided);
			int rotX = 0;
			int rotY = 0;
			boolean uvLock = false;
			if(data.getAsJsonObject().has("x"))
				rotX = data.getAsJsonObject().get("x").getAsInt();
			if(data.getAsJsonObject().has("y"))
				rotY = data.getAsJsonObject().get("y").getAsInt();
			if(data.getAsJsonObject().has("uvlock"))
				uvLock = data.getAsJsonObject().get("uvlock").getAsBoolean();
			Model model = new Model(ModelRegistry.getModel(modelId));
			if(rotX != 0 || rotY != 0)
				model.rotate(rotX, rotY, uvLock);
			models.add(model);
		}
	}
	
	@Override
	public boolean needsConnectionInfo() {
		return this._needsConnectionInfo;
	}

	@Override
	public boolean usePart(TAG_Compound properties, int x, int y, int z) {
		if(checks.isEmpty())
			return true;
		
		for(Map<String, String> check : checks) {
			boolean res = doCheck(properties, check, x, y, z);
			if(res)
				return true;
		}
		return false;
	}
	
	private boolean doCheck(TAG_Compound properties, Map<String, String> check, int x, int y, int z) {
		for(NBT_Tag tag : properties.elements) {
			String value = check.get(tag.getName());
			String propValue = null;
			if(value != null) {
				switch(tag.ID()) {
				case 1:
					// Byte
					propValue = Byte.toString(((TAG_Byte)tag).value);
					break;
				case 2:
					// Short
					propValue = Short.toString(((TAG_Short)tag).value);
					break;
				case 3:
					// Int
					propValue = Integer.toString(((TAG_Int)tag).value);
					break;
				case 4:
					// Long
					propValue = Long.toString(((TAG_Long)tag).value);
					break;
				case 5:
					// Float
					propValue = Float.toString(((TAG_Float)tag).value);
					break;
				case 6:
					// Double
					propValue = Double.toString(((TAG_Double)tag).value);
					break;
				case 8:
					// String
					propValue = ((TAG_String)tag).value;
					break;
				default:
					break;
				}
				
				if(propValue != null) {
					if(!value.equals(propValue)) {
						if(!((value.equals("false") && propValue.equals("0")) || (value.equals("true") && propValue.equals("1"))))
							return false;
					}
				}
			}
		}
		// Check for connection info
		if(needsConnectionInfo()) {
			for(Entry<String, String> entry : check.entrySet()) {
				if(entry.getKey().startsWith("miex_connect")) {
					// Test the neighbouring block.
					boolean res = testMiExConnection(entry.getKey(), entry.getValue(), x, y, z);
					// If it doesn't match, then we shouldn't use this part.
					if(!res)
						return false;
				}
			}
		}
		return true;
	}

}
