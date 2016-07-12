package org.black.kotlin.j2seprojectextension.classpath;

import java.beans.PropertyChangeEvent;
import org.netbeans.spi.java.classpath.ClassPathImplementation;
import org.netbeans.spi.java.classpath.PathResourceImplementation;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.netbeans.api.java.classpath.ClassPath;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import org.black.kotlin.j2seprojectextension.lookup.KotlinProjectHelper;
import org.black.kotlin.utils.KotlinClasspath;
import org.netbeans.api.java.platform.JavaPlatform;
import org.netbeans.api.java.platform.JavaPlatformManager;
import org.netbeans.api.project.Project;
import org.netbeans.spi.project.support.ant.PropertyEvaluator;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.WeakListeners;

final class BootClassPathImplementation implements ClassPathImplementation, PropertyChangeListener {

    private static final String PLATFORM_ACTIVE = "platform.active";   
    private static final String ANT_NAME = "java.platform.ant.name";          
    private static final String J2SE = "j2se";                                 

    private final PropertyEvaluator evaluator;
    private JavaPlatformManager platformManager;
    //name of project active platform
    private String activePlatformName;
    private List<PathResourceImplementation> resourcesCache;
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    private long eventId;
    private boolean isActivePlatformValid;
    private final Project project;

    public BootClassPathImplementation(Project project, PropertyEvaluator evaluator) {
        assert evaluator != null;
        this.evaluator = evaluator;
        this.project = project;
        evaluator.addPropertyChangeListener(WeakListeners.propertyChange(BootClassPathImplementation.this, evaluator));
    }

    @Override
    public List<PathResourceImplementation> getResources() {
        long currentId;
        synchronized (this) {
            if (this.resourcesCache != null) {
                return this.resourcesCache;
            }
            currentId = eventId;
        }
        
        JavaPlatform jp = findActivePlatform ();
        final List<PathResourceImplementation> result = new ArrayList<PathResourceImplementation>();
        if (jp != null) {
            //TODO: May also listen on CP, but from Platform it should be fixed.            
            final ClassPath cp = jp.getBootstrapLibraries();
            assert cp != null : jp;
            for (ClassPath.Entry entry : cp.entries()) {
                result.add(ClassPathSupport.createResource(entry.getURL()));
            }
        }
        
        
        //Kotlin boot classpath
        List<URL> kotlinBoot = getKotlinBootClassPath();
        for (URL url : kotlinBoot){
            result.add(ClassPathSupport.createResource(url));
        }
        result.add(ClassPathSupport.createResource(KotlinProjectHelper.INSTANCE.getLightClassesDirectory(project).toURL()));
        //

        
        synchronized (this) {
            if (currentId == eventId) {
                if (this.resourcesCache == null) {
                    this.resourcesCache = Collections.unmodifiableList(result);
                }
                return this.resourcesCache;
            }
            else {
                return Collections.unmodifiableList (result);
            }           
        }       
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.support.addPropertyChangeListener (listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        this.support.removePropertyChangeListener (listener);
    }

    public List<URL> getKotlinBootClassPath(){
        List<String> paths = new ArrayList<String>();
        List<URL> urls = new ArrayList<URL>();
        paths.add(KotlinClasspath.getKotlinBootClasspath());
        for (String path : paths) {
            File file = new File(path);
            if (!file.canRead()) {
                continue;
            }

            FileObject fileObject = FileUtil.toFileObject(file);
            if (FileUtil.isArchiveFile(fileObject)) {
                fileObject = FileUtil.getArchiveRoot(fileObject);
            }
            if (fileObject != null) {
                urls.add(fileObject.toURL());
            }
        }
        return urls;
    }
    
    protected JavaPlatform findActivePlatform () {
        if (this.platformManager == null) {
            this.platformManager = JavaPlatformManager.getDefault();
            this.platformManager.addPropertyChangeListener(WeakListeners.propertyChange(this, this.platformManager));
        }                
        this.activePlatformName = evaluator.getProperty(PLATFORM_ACTIVE);
        final JavaPlatform activePlatform = platformManager.getDefaultPlatform();
        this.isActivePlatformValid = activePlatform != null;
        return activePlatform;
    }
    
//    protected ScalaPlatform findActiveScalaPlatform () {
//        if (this.scalaPlatformManager == null) {
//            this.scalaPlatformManager = ScalaPlatformManager.getDefault();
//            this.scalaPlatformManager.addPropertyChangeListener(WeakListeners.propertyChange(this, this.platformManager));
//        }                
//        this.activeScalaPlatformName = evaluator.getProperty(SCALA_PLATFORM_ACTIVE);
////        final ScalaPlatform activePlatform = scalaPlatformManager.getDefaultPlatform();
//        final JavaPlatform activePlatform = J2SEProjectUtil.getActivePlatform (this.activePlatformName);
////        this.isActiveScalaPlatformValid = activePlatform != null;
//        return activePlatform;
//    }
    
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getSource() == this.evaluator && evt.getPropertyName().equals(PLATFORM_ACTIVE)) {
            //Active platform was changed
            resetCache ();
        }
        else if (evt.getSource() == this.platformManager && JavaPlatformManager.PROP_INSTALLED_PLATFORMS.equals(evt.getPropertyName()) && activePlatformName != null) {

        }
    }
    
    /**
     * Resets the cache and firesPropertyChange
     */
    private void resetCache () {
        synchronized (this) {
            resourcesCache = null;
            eventId++;
        }
        support.firePropertyChange(PROP_RESOURCES, null, null);
    }
    
}