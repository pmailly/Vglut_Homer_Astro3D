/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package VglutHomerTools;

import mcib3d.geom.Voxel3D;

/**
 *
 * @author phm
 */
public class Synapse_Vglut_Homer {
    private double distDots;
    // distance to astrocyte
    private double distAstro;
    // vglut homer voxel
    private Voxel3D vglutV, homerV, synV;
   
	
	public Synapse_Vglut_Homer(Voxel3D synV, double distDots, double distAstro, Voxel3D vglutV, Voxel3D homerV) {
            this.synV = synV;
            this.distDots = distDots;
            this.distAstro = distAstro;
            this.vglutV = vglutV;
            this.homerV = homerV;
            
	}
        
        public void setSynV(Voxel3D synV) {
            this.synV = synV;
	}
        
        public void setDistDots(double distDots) {
            this.distDots = distDots;
	}
        
        public void setDistAstro(double distAstro) {
            this.distAstro = distAstro;
	}
        
        public void setVglutV(Voxel3D vglutV) {
            this.vglutV = vglutV;
	}
        
        public void setHomerV(Voxel3D homerV) {
            this.homerV = homerV;
	}
        
        public Voxel3D getSynV() {
            return synV;
        }
        
        public double getDistDots() {
            return distDots;
        }
        
        public double getDistAstro() {
            return distAstro;
        }
        
        public Voxel3D getVglutV() {
            return vglutV;
        }
        
        public Voxel3D getHomerV() {
            return homerV;
        }
        
}
