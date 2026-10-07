"use client";

import React from "react";
import { motion } from "framer-motion";

interface AbstractMinimapProps {
  progress?: number; // 0 to 1
}

export function AbstractMinimap({ progress = 0 }: AbstractMinimapProps) {
  return (
    <div className="relative w-full h-[120px] flex items-center justify-center overflow-hidden">
      {/* Background Dot Grid */}
      <div 
        className="absolute inset-0 opacity-20"
        style={{
          backgroundImage: "radial-gradient(#000 1.5px, transparent 1.5px)",
          backgroundSize: "16px 16px"
        }}
      />
      
      {/* Geometric Path */}
      <svg 
        width="200" 
        height="120" 
        viewBox="0 0 200 120" 
        fill="none" 
        xmlns="http://www.w3.org/2000/svg"
        className="absolute z-0"
      >
        <path 
          d="M 100 120 L 100 60 C 100 40 120 40 120 40 L 200 40" 
          stroke="#0052FF" 
          strokeWidth="20" 
          strokeLinecap="round" 
          strokeLinejoin="round" 
          strokeDasharray="200"
          strokeDashoffset={200 - (200 * progress)}
          className="transition-all duration-1000 ease-in-out"
        />
        {/* Trailed path (past) */}
        <path 
          d="M 100 120 L 100 80" 
          stroke="#000000" 
          strokeWidth="20" 
          strokeOpacity="0.10"
          strokeLinecap="round" 
        />
      </svg>

      {/* User Marker */}
      <motion.div 
        className="absolute z-10 w-[24px] h-[24px] bg-primary rounded-full border-[4px] border-white shadow-[0_0_0_2px_rgba(0,82,255,0.2)]"
        style={{ bottom: "32px" }}
        animate={{
          boxShadow: [
            "0 0 0 2px rgba(0,82,255,0.2)",
            "0 0 0 12px rgba(0,82,255,0)",
            "0 0 0 2px rgba(0,82,255,0)"
          ]
        }}
        transition={{
          duration: 1.5,
          repeat: Infinity,
          ease: "easeInOut"
        }}
      />
    </div>
  );
}
