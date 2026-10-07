"use client";

import React from "react";

export function WearableSimulator({ children }: { children: React.ReactNode }) {
  return (
    <div className="relative flex items-center justify-center">
      {/* Top Strap */}
      <div className="absolute -top-12 w-[160px] h-[80px] bg-gradient-to-b from-[#222222] to-[#0A0A0A] rounded-t-3xl border-t border-x border-[#333333] shadow-[inset_0_4px_10px_rgba(0,0,0,0.5)] z-0" />
      
      {/* Bottom Strap */}
      <div className="absolute -bottom-12 w-[160px] h-[80px] bg-gradient-to-t from-[#222222] to-[#0A0A0A] rounded-b-3xl border-b border-x border-[#333333] shadow-[inset_0_-4px_10px_rgba(0,0,0,0.5)] z-0" />

      {/* Side Crown (Hardware Detail) */}
      <div className="absolute -right-[6px] top-1/2 -translate-y-1/2 w-[12px] h-[44px] bg-gradient-to-r from-[#555555] via-[#333333] to-[#111111] rounded-r-md border-y border-r border-[#666666] shadow-[2px_0_10px_rgba(0,0,0,0.5)] pointer-events-none z-0" />

      {/* Device Body / Outer Frame */}
      <div className="relative w-[380px] h-[380px] rounded-full bg-gradient-to-br from-[#444444] via-[#1A1A1A] to-[#0A0A0A] shadow-[0_20px_50px_rgba(0,0,0,0.7)] flex items-center justify-center p-[2px] border border-[#555555] ring-1 ring-black/50 z-10">
        
        {/* Inner Bezel */}
        <div className="relative w-[360px] h-[360px] rounded-full bg-[#050505] flex items-center justify-center shadow-[inset_0_5px_20px_rgba(0,0,0,1)] border border-[#111111]">
          
          {/* CRITICAL: Display Surface explicitly enforcing #FFFFFF */}
          <div 
            className="relative w-[340px] h-[340px] rounded-full overflow-hidden flex flex-col z-10"
            style={{ backgroundColor: "#FFFFFF" }}
          >
            {children}
          </div>

          {/* Glass / Reflection Overlay */}
          <div className="absolute inset-0 rounded-full pointer-events-none z-20 overflow-hidden">
            {/* Primary curved reflection */}
            <div className="absolute -top-[10%] -left-[10%] w-[120%] h-[45%] bg-gradient-to-b from-white/10 to-transparent transform -rotate-12 blur-[1px]" />
            {/* Edge highlight */}
            <div className="absolute inset-0 rounded-full border border-white/5" />
          </div>

        </div>
      </div>
    </div>
  );
}
