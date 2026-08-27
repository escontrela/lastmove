package com.escontrela.lastmove.domain.service;

import com.escontrela.lastmove.domain.common.*;
import com.escontrela.lastmove.domain.game.*;
import java.util.*;

/** Pure attack-map calculation used by presentation hints. */
public final class ThreatenedSquaresService {
  public Set<Square> attackedBy(PositionSnapshot position, PieceColor color) {
    Map<Square, PositionPiece> board=new HashMap<>(); position.pieces().forEach(p->board.put(p.square(),p)); Set<Square> result=new HashSet<>(); position.pieces().stream().filter(p->p.color()==color).forEach(p->add(result,board,p)); return Set.copyOf(result);
  }
  private void add(Set<Square> out,Map<Square,PositionPiece>b,PositionPiece p){int f=p.square().getFile(),r=p.square().getRank();switch(p.type()){case PAWN->{step(out,f-1,r+(p.color()==PieceColor.WHITE?1:-1));step(out,f+1,r+(p.color()==PieceColor.WHITE?1:-1));}case KNIGHT->{for(int[]d:new int[][]{{1,2},{2,1},{2,-1},{1,-2},{-1,-2},{-2,-1},{-2,1},{-1,2}})step(out,f+d[0],r+d[1]);}case KING->{for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)if(x!=0||y!=0)step(out,f+x,r+y);}case BISHOP->rays(out,b,f,r,new int[][]{{1,1},{1,-1},{-1,1},{-1,-1}});case ROOK->rays(out,b,f,r,new int[][]{{1,0},{-1,0},{0,1},{0,-1}});case QUEEN->rays(out,b,f,r,new int[][]{{1,1},{1,-1},{-1,1},{-1,-1},{1,0},{-1,0},{0,1},{0,-1}});}}
  private void rays(Set<Square>o,Map<Square,PositionPiece>b,int f,int r,int[][]d){for(int[]v:d)for(int x=f+v[0],y=r+v[1];valid(x,y);x+=v[0],y+=v[1]){Square s=Square.of(x,y);o.add(s);if(b.containsKey(s))break;}}
  private void step(Set<Square>o,int f,int r){if(valid(f,r))o.add(Square.of(f,r));}private boolean valid(int f,int r){return f>=0&&f<8&&r>=0&&r<8;}
}
