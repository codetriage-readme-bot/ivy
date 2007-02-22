/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.ivy.core.cache;

import java.io.File;

import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.DefaultModuleRevision;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorParser;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.util.Message;
import org.apache.ivy.util.PropertiesFile;

public class CacheManager {
	public static CacheManager getInstance(IvySettings settings, File cache) {
		return new CacheManager(settings, cache);
	}

	public static CacheManager getInstance(IvySettings settings) {
		return getInstance(settings, settings.getDefaultCache());
	}
	
	private IvySettings _settings;
	private File _cache;

    public CacheManager(IvySettings settings, File cache) {
		_settings = settings;
		_cache = cache;
	}

	public File getResolvedIvyFileInCache(ModuleRevisionId mrid) {
        return new File(_cache, IvyPatternHelper.substitute(_settings.getCacheResolvedIvyPattern(), mrid.getOrganisation(), mrid.getName(), mrid.getRevision(), "ivy", "ivy", "xml"));
    }

    public File getResolvedIvyPropertiesInCache(ModuleRevisionId mrid) {
        return new File(_cache, IvyPatternHelper.substitute(_settings.getCacheResolvedIvyPropertiesPattern(), mrid.getOrganisation(), mrid.getName(), mrid.getRevision(), "ivy", "ivy", "xml"));
    }

    public File getIvyFileInCache(ModuleRevisionId mrid) {
        return new File(_cache, IvyPatternHelper.substitute(_settings.getCacheIvyPattern(), DefaultArtifact.newIvyArtifact(mrid, null)));
    }

    /**
     * Returns a File object pointing to where the artifact can be found on the local file system.
     * This is usually in the cache, but it can be directly in the repository if it is local
     * and if the resolve has been done with useOrigin = true
     * 
     */
    public File getArchiveFileInCache(Artifact artifact) {
    	ArtifactOrigin origin = getSavedArtifactOrigin(artifact);
		return getArchiveFileInCache(artifact, origin);
    }
    
    /**
     * Returns a File object pointing to where the artifact can be found on the local file system.
     * This is usually in the cache, but it can be directly in the repository if it is local
     * and if the resolve has been done with useOrigin = true
     * 
     */
    public File getArchiveFileInCache(Artifact artifact, ArtifactOrigin origin) {
    	File archive = new File(_cache, getArchivePathInCache(artifact, origin));
    	if (!archive.exists() && origin != null && origin.isLocal()) {
    		File original = new File(origin.getLocation());
    		if (original.exists()) {
    			return original;
    		}
    	}
    	return archive;
    }
    /**
     * Returns a File object pointing to where the artifact can be found on the local file system,
     * using or not the original location depending on the availability of origin information provided
     * as parameter and the setting of useOrigin.
     * 
     * If useOrigin is false, this method will always return the file in the cache.
     * 
     */
    public File getArchiveFileInCache(Artifact artifact, ArtifactOrigin origin, boolean useOrigin) {
    	if (useOrigin && origin != null && origin.isLocal()) {
    		return new File(origin.getLocation());
    	} else {
    		return new File(_cache, getArchivePathInCache(artifact, origin));
    	}
    }
    
    
    public String getArchivePathInCache(Artifact artifact) {
        return IvyPatternHelper.substitute(_settings.getCacheArtifactPattern(), artifact);
    }

    public String getArchivePathInCache(Artifact artifact, ArtifactOrigin origin) {
        return IvyPatternHelper.substitute(_settings.getCacheArtifactPattern(), artifact, origin);
    }

    /**
     * Saves the information of which resolver was used to resolve a md,
     * so that this info can be retrieve later (even after a jvm restart)
     * by getSavedResolverName(ModuleDescriptor md)
     * @param md the module descriptor resolved
     * @param name resolver name
     */
    public void saveResolver(ModuleDescriptor md, String name) {
        PropertiesFile cdf = getCachedDataFile(md);
        cdf.setProperty("resolver", name);
        cdf.save();
    }

    /**
     * Saves the information of which resolver was used to resolve a md,
     * so that this info can be retrieve later (even after a jvm restart)
     * by getSavedArtResolverName(ModuleDescriptor md)
     * @param md the module descriptor resolved
     * @param name artifact resolver name
     */
    public void saveArtResolver(ModuleDescriptor md, String name) {
        PropertiesFile cdf = getCachedDataFile(md);
        cdf.setProperty("artifact.resolver", name);
        cdf.save();
    }
    
    public void saveArtifactOrigin(Artifact artifact, ArtifactOrigin origin) {
       PropertiesFile cdf = getCachedDataFile(artifact.getModuleRevisionId());
       cdf.setProperty("artifact." + artifact.getName() + "#" + artifact.getExt() + ".is-local", String.valueOf(origin.isLocal()));
       cdf.setProperty("artifact." + artifact.getName() + "#" + artifact.getExt() + ".location", origin.getLocation());
       cdf.save();
    }
    
    public ArtifactOrigin getSavedArtifactOrigin(Artifact artifact) {
        PropertiesFile cdf = getCachedDataFile(artifact.getModuleRevisionId());
        String location = cdf.getProperty("artifact." + artifact.getName() + "#" + artifact.getExt() + ".location");
        boolean isLocal = Boolean.valueOf(cdf.getProperty("artifact." + artifact.getName() + "#" + artifact.getExt() + ".is-local")).booleanValue();
        
        if (location == null) {
           // origin has not been specified, return null
           return null;
        }
        
        return new ArtifactOrigin(isLocal, location);
    }
    
    public void removeSavedArtifactOrigin(Artifact artifact) {
        PropertiesFile cdf = getCachedDataFile(artifact.getModuleRevisionId());
        cdf.remove("artifact." + artifact.getName() + "#" + artifact.getExt() + ".location");
        cdf.remove("artifact." + artifact.getName() + "#" + artifact.getExt() + ".is-local");
        cdf.save();
    }
    
    private String getSavedResolverName(ModuleDescriptor md) {
        PropertiesFile cdf = getCachedDataFile(md);
        return cdf.getProperty("resolver");
    }

    private String getSavedArtResolverName(ModuleDescriptor md) {
        PropertiesFile cdf = getCachedDataFile(md);
        return cdf.getProperty("artifact.resolver");
    }

    private PropertiesFile getCachedDataFile(ModuleDescriptor md) {
       return getCachedDataFile(md.getResolvedModuleRevisionId());
    }
    
    private PropertiesFile getCachedDataFile(ModuleRevisionId mRevId) {
        return new PropertiesFile(new File(_cache, IvyPatternHelper.substitute(_settings.getCacheDataFilePattern(),mRevId)), "ivy cached data file for "+mRevId);
    }

    public ResolvedModuleRevision findModuleInCache(ModuleRevisionId mrid, boolean validate) {
        // first, check if it is in cache
        if (!_settings.getVersionMatcher().isDynamic(mrid)) {
            File ivyFile = getIvyFileInCache(mrid);
            if (ivyFile.exists()) {
                // found in cache !
                try {
                    ModuleDescriptor depMD = XmlModuleDescriptorParser.getInstance().parseDescriptor(_settings, ivyFile.toURL(), validate);
                    String resolverName = getSavedResolverName(depMD);
                    String artResolverName = getSavedArtResolverName(depMD);
                    DependencyResolver resolver = _settings.getResolver(resolverName);
                    if (resolver == null) {
                        Message.debug("\tresolver not found: "+resolverName+" => trying to use the one configured for "+mrid);                                    
                        resolver = _settings.getResolver(depMD.getResolvedModuleRevisionId().getModuleId());
                        if (resolver != null) {
                            Message.debug("\tconfigured resolver found for "+depMD.getResolvedModuleRevisionId()+": "+resolver.getName()+": saving this data");                                    
                            saveResolver(depMD, resolver.getName());
                        }
                    }
                    DependencyResolver artResolver = _settings.getResolver(artResolverName);
                    if (artResolver == null) {
                        artResolver = resolver;
                    }
                    if (resolver != null) {
                        Message.debug("\tfound ivy file in cache for "+mrid+" (resolved by "+resolver.getName()+"): "+ivyFile);
                        return new DefaultModuleRevision(resolver, artResolver, depMD, false, false, ivyFile.toURL());
                    } else {
                        Message.debug("\tresolver not found: "+resolverName+" => cannot use cached ivy file for "+mrid);                                    
                    }
                } catch (Exception e) {
                    // will try with resolver
                    Message.debug("\tproblem while parsing cached ivy file for: "+mrid+": "+e.getMessage());                                    
                }
            } else {
                Message.debug("\tno ivy file in cache for "+mrid+": tried "+ivyFile);
            }
        }
        return null;
    }


    public String toString() {
    	return "cache: "+String.valueOf(_cache);
    }

	public File getCache() {
		return _cache;
	}

}